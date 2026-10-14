package com.ygb.abroad.ai;

/**
 * 输出过滤命中项
 *
 * @author ygb
 */
public class AiFilterHit
{
    /** 类别编码（过滤词文件名，如 admission_promise；整篇文书为 essay） */
    private String type;

    /** 类别名称（如 录取承诺） */
    private String label;

    /** 命中的词或说明 */
    private String word;

    /** 命中字段（JSON Pointer） */
    private String path;

    public AiFilterHit()
    {
    }

    public AiFilterHit(String type, String label, String word, String path)
    {
        this.type = type;
        this.label = label;
        this.word = word;
        this.path = path;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getWord()
    {
        return word;
    }

    public void setWord(String word)
    {
        this.word = word;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    @Override
    public String toString()
    {
        return label + "：" + word + "（" + path + "）";
    }
}
