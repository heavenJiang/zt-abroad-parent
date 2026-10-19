package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * 管理端：批量开放顾问时段
 *
 * @author ygb
 */
public class AbrSlotBatchBody
{
    /** 顾问（sys_user） */
    private Long userId;

    /** 开始日期（yyyy-MM-dd） */
    private String from;

    /** 结束日期（含） */
    private String to;

    /** 星期几（1 周一 … 7 周日；空为每天） */
    private List<Integer> weekdays;

    /** 时间段（如 09:00-12:00） */
    private List<String> ranges;

    /** 每个时段的分钟数 */
    private Integer minutes;

    /** 方式（online / offline） */
    private String mode;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getFrom()
    {
        return from;
    }

    public void setFrom(String from)
    {
        this.from = from;
    }

    public String getTo()
    {
        return to;
    }

    public void setTo(String to)
    {
        this.to = to;
    }

    public List<Integer> getWeekdays()
    {
        return weekdays;
    }

    public void setWeekdays(List<Integer> weekdays)
    {
        this.weekdays = weekdays;
    }

    public List<String> getRanges()
    {
        return ranges;
    }

    public void setRanges(List<String> ranges)
    {
        this.ranges = ranges;
    }

    public Integer getMinutes()
    {
        return minutes;
    }

    public void setMinutes(Integer minutes)
    {
        this.minutes = minutes;
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }
}
