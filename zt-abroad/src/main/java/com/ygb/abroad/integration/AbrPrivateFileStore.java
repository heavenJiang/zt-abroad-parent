package com.ygb.abroad.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.common.config.ZtConfig;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import com.ygb.common.utils.uuid.IdUtils;

/**
 * 私有文件存储（简历、成绩单原件）：本机私有目录，不经 /profile 静态资源对外提供，只有服务端读取
 * <p>
 * 相对路径形如 2026/10/17/&lt;uuid&gt;.pdf；读写前校验路径不越出根目录。生产改用对象存储私有桶时替换本类。
 * </p>
 *
 * @author ygb
 */
@Component
public class AbrPrivateFileStore
{
    private static final Logger log = LoggerFactory.getLogger(AbrPrivateFileStore.class);

    @Autowired
    private AbrResumeProperties properties;

    /**
     * 保存文件
     *
     * @param bytes 内容
     * @param ext 扩展名（已校验）
     * @return 相对路径
     */
    public String save(byte[] bytes, String ext)
    {
        String relative = new SimpleDateFormat("yyyy/MM/dd").format(new Date()) + "/" + IdUtils.fastSimpleUUID() + "." + ext;
        Path target = resolve(relative);
        try
        {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        }
        catch (IOException e)
        {
            log.error("私有文件保存失败 {}", relative, e);
            throw new ServiceException("文件保存失败，请稍后再试");
        }
        return relative;
    }

    /**
     * 读取文件
     *
     * @param relative 相对路径
     * @return 内容
     * @throws IOException 文件不存在或读取失败
     */
    public byte[] read(String relative) throws IOException
    {
        return Files.readAllBytes(resolve(relative));
    }

    /**
     * 删除文件（不存在视为已删除）
     *
     * @param relative 相对路径，为空时忽略
     * @return 是否已不存在
     */
    public boolean delete(String relative)
    {
        if (StringUtils.isEmpty(relative))
        {
            return true;
        }
        try
        {
            Files.deleteIfExists(resolve(relative));
            return true;
        }
        catch (IOException | ServiceException e)
        {
            log.warn("私有文件删除失败 {}：{}", relative, e.getMessage());
            return false;
        }
    }

    /**
     * 根目录（未配置时为 zt.profile 的同级目录 private/resume）
     *
     * @return 绝对路径
     */
    Path root()
    {
        String dir = properties.getDir();
        if (StringUtils.isEmpty(dir))
        {
            dir = Paths.get(ZtConfig.getProfile()).toAbsolutePath().normalize().resolveSibling("private").resolve("resume").toString();
        }
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    private Path resolve(String relative)
    {
        Path root = root();
        Path profile = StringUtils.isEmpty(ZtConfig.getProfile()) ? null : Paths.get(ZtConfig.getProfile()).toAbsolutePath().normalize();
        if (profile != null && root.startsWith(profile))
        {
            throw new ServiceException("简历存放目录不能位于公开上传目录下，请修改 abroad.resume.dir");
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root))
        {
            throw new ServiceException("文件路径不合法");
        }
        return target;
    }
}
