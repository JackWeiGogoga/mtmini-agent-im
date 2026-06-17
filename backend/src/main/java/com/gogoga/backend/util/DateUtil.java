package com.gogoga.backend.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 日期API的工具类
 *
 **/
public class DateUtil {
    public static final int FIVE_MINUTES = 5 * 60 * 1000;
    public static final int ONE_HOUR_MILLS = 60 * 60 * 1000;
    public static final int ONE_DAYS_MILLS = 24 * 60 * 60 * 1000;
    public static final int TWO_DAYS_MILLS = 2 * 24 * 60 * 60 * 1000;
    public static final long THIRTY_DAYS_MILLS = 30L * 24 * 60 * 60 * 1000;

    /**
     * 自定义日期格式化器
     */
    public final static String DATE_FORMAT_YMDHMS = "yyyy-MM-dd HH:mm:ss";
    public final static DateTimeFormatter YYYY_MM_DD_HH_MM_SS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public final static DateTimeFormatter YYYY_MM_DD_HH_MM_SS_MS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public final static DateTimeFormatter YYYY_MM_DD_HH_MM_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public final static DateTimeFormatter YYYY_MM_DD_HH_MM_SS_FORMAT3 =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    public final static DateTimeFormatter YYYY_MM_DD_HH_MM_SS_SSS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    public final static DateTimeFormatter YYYY_MM_DD_HH_MM_SS_FORMAT2 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public final static DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public final static DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    public final static DateTimeFormatter YYMM = DateTimeFormatter.ofPattern("yyMM");
    public final static DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    public final static DateTimeFormatter YYYYMMDD2 = DateTimeFormatter.ofPattern("yyyyMMdd");
    public final static DateTimeFormatter YYYYMMDD3 = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    public final static DateTimeFormatter MM_DD_HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    public final static String YYYYMMDD_STR = "yyyy-MM-dd";
    public final static String YYYYMMDD2_STR = "yyyyMMdd";
    public final static String YYYYMMDDHHMMSS_STR = "yyyy-MM-dd HH:mm:ss";

    public final static DateTimeFormatter YYYYMMDD4 = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 获取当前时间.
     *
     * @return 当前时间
     */
    public static Date currentDate() {
        return new Date();
    }

    /**
     * java.util.Date 转为Java8的LocalDate
     *
     * @param date
     * @return
     */
    public static LocalDate dateToLocalDate(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * java.util.Date 转为Java8的LocalDateTime
     *
     * @param date
     * @return
     */
    public static LocalDateTime dateToLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * java8 localDateTime 转为java.util.Date
     *
     * @param localDateTime
     * @return
     */
    public static Date localDateTimeToDate(LocalDateTime localDateTime) {
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = localDateTime.atZone(zone).toInstant();
        return Date.from(instant);
    }

    /**
     * Java8的LocalDate 转为java.util.localDat
     *
     * @param localDate
     * @return
     */
    public static Date localDateToDate(LocalDate localDate) {
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = localDate.atStartOfDay().atZone(zone).toInstant();
        return Date.from(instant);
    }

    /**
     * 加几天
     *
     * @param date
     * @param offset
     * @return
     */
    public static Date plusByDay(Date date, long offset) {
        return localDateToDate(dateToLocalDate(date).plusDays(offset));
    }

    /**
     * 减几天
     *
     * @param date
     * @param offset
     * @return
     */
    public static Date minusByDay(Date date, long offset) {
        return localDateTimeToDate(dateToLocalDateTime(date).minusDays(offset));
    }

    /**
     * 减几个星期
     *
     * @param date
     * @param offset
     * @return
     */
    public static Date minusByWeek(Date date, long offset) {
        return localDateToDate(dateToLocalDate(date).minusWeeks(offset));
    }

    /**
     * 加几秒
     *
     * @param date
     * @param offset
     * @return
     */
    public static Date plusBySeconds(Date date, long offset) {
        return localDateTimeToDate(dateToLocalDateTime(date).plusSeconds(offset));
    }

    public static Date plusByMinutes(Date date, long offsets) {
        return localDateTimeToDate(dateToLocalDateTime(date).plusMinutes(offsets));
    }

    /**
     * 加小时,
     *
     * @param date    时间
     * @param offsets 偏移量,可以为负数
     * @return
     */
    public static Date plusByHours(Date date, long offsets) {
        return localDateTimeToDate(dateToLocalDateTime(date).plusHours(offsets));
    }

    /**
     * 减几个月
     *
     * @param date    日期
     * @param offsets
     * @return
     */
    public static Date minusByMonth(Date date, int offsets) {

        return localDateTimeToDate(dateToLocalDateTime(date).minusMonths(offsets));
    }

    /**
     * 减几小时
     *
     * @param date    日期
     * @param offsets
     * @return
     */
    public static Date minusByHour(Date date, int offsets) {

        return localDateTimeToDate(dateToLocalDateTime(date).minusHours(offsets));
    }

    /**
     * java8 unix时间戳 转 LocalDateTime
     *
     * @param unixTime
     * @return
     */
    public static LocalDateTime unixToLocalDateTime(long unixTime) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixTime), ZoneId.systemDefault());
    }

    /**
     * 指定String 类型及日期格式转换为java.util.date 类型
     *
     * @param timeStr String 日期
     * @param format  String 日期格式
     * @return
     */
    public static Date dayTimeToDate(String timeStr, String format) {
        try {
            return localDateTimeToDate(toLocalDateTime(timeStr, format));
        } catch (Exception e) {
            return null;
        }
    }

    public static Date dayToDate(String dayStr, String format) {
        return localDateToDate(toLocalDate(dayStr, format));
    }

    /**
     * 指定String date类型及日期格式转换为java.util.localDateTime 类型
     *
     * @param dateStr String 日期
     * @param format  String 日期格式
     * @return
     */
    public static LocalDateTime toLocalDateTime(String dateStr, String format) {
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(format));
    }

    /**
     * 指定String date类型及日期格式转换为java.util.LocalDate 类型
     *
     * @param dateStr String 日期
     * @param format  String 日期格式
     * @return
     */
    public static LocalDate toLocalDate(String dateStr, String format) {
        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(format));
    }

    /**
     * 日期转字符串.
     *
     * @param date
     * @return
     */
    public static String dateToString(Date date) {
        return dateToString(date, YYYY_MM_DD_HH_MM_SS_FORMAT);
    }

    public static String dateToStringWithoutSeconds(Date date) {
        return dateToString(date, YYYY_MM_DD_HH_MM_FORMAT);
    }

    /**
     * 日期转字符串.
     *
     * @param date 日期
     * @return
     */
    public static String dateToString(Date date, DateTimeFormatter formatter) {
        return formatter.format(dateToLocalDateTime(date));
    }

    /**
     * 时间戳转字符串
     *
     * @param timestamp 时间戳
     * @return java.lang.String
     */
    public static String timestampToString(Long timestamp) {
        return timestampToString(timestamp, YYYY_MM_DD_HH_MM_SS_FORMAT, null);
    }

    /**
     * 时间戳转带毫秒字符串
     *
     * @param timestamp 时间戳
     * @return java.lang.String
     */
    public static String timestampToMsString(Long timestamp) {
        return timestampToString(timestamp, YYYY_MM_DD_HH_MM_SS_MS_FORMAT, null);
    }

    /**
     * 时间戳转字符串
     *
     * @param timestamp 时间戳
     * @param formatter 格式化
     * @param defaultValue
     * @return java.lang.String
     */
    public static String timestampToString(Long timestamp, DateTimeFormatter formatter, String defaultValue) {
        if (Objects.isNull(timestamp) || Objects.isNull(formatter)) {
            return defaultValue;
        }

        return dateToString(new Date(timestamp), formatter);
    }

    /**
     * 获得指定日期的一天结束时间
     *
     * @param date 传入的指定日期
     * @return
     */
    public static Date getEndOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return localDateTimeToDate(endOfDay);
    }

    /**
     * 获得指定日期的一天开始时间
     *
     * @param date 传入的指定日期
     * @return
     */
    public static Date getStartOfDay(Date date) {
        LocalDate localDate = dateToLocalDate(date);
        LocalDateTime startOfDay = localDate.atStartOfDay();
        return localDateTimeToDate(startOfDay);

    }

    /**
     * 获得此日期位于的周的第一天的具体日期
     *
     * @param date
     * @return
     */
    public static Date getFirstDateOfWeek(Date date) {
        TemporalField fieldISO = WeekFields.of(Locale.PRC).dayOfWeek();
        return localDateToDate(dateToLocalDate(date).with(fieldISO, 2));
    }

    /**
     * 获得此日期位于的周的最后一天的具体日期
     *
     * @param date
     * @return
     */
    public static Date getLastDateOfWeek(Date date) {
        TemporalField fieldISO = WeekFields.of(Locale.PRC).dayOfWeek();
        return plusByDay(localDateToDate(dateToLocalDate(date).with(fieldISO, 7)), 1);
    }

    /**
     * 获得指定日期的位于的月份的当月最后一天
     *
     * @param date
     * @return
     */
    public static Date getLastDateOfMonth(Date date) {

        LocalDate initialDate = dateToLocalDate(date);
        return localDateToDate(initialDate.withDayOfMonth(initialDate.lengthOfMonth()));
    }

    /**
     * 获得指定日期的位于的月份的当月起始一天
     *
     * @param date
     * @return
     */
    public static Date getFirstDateOfMonth(Date date) {

        return localDateToDate(dateToLocalDate(date).withDayOfMonth(1));
    }

    /**
     * 两个日期相差的分钟数
     *
     * @param date1
     * @param date2
     * @return
     */
    public static int minutesBetween(Date date1, Date date2) {
        LocalDateTime dateTime = dateToLocalDateTime(date1);
        LocalDateTime dateTime2 = dateToLocalDateTime(date2);
        return Long.valueOf(ChronoUnit.MINUTES.between(dateTime, dateTime2)).intValue();
    }

    /**
     * 当天剩余秒数
     *
     * @return
     */
    public static long getDayRemainSeconds() {
        LocalDateTime midnight = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), midnight);
        return seconds;
    }

    /**
     * 获取本月当天的号数
     *
     * @return
     */
    public static int getCurrentDayOfMonth() {
        return LocalDateTime.now().getDayOfMonth();
    }

    /**
     * 计算两个时间戳相差天数 不足一天按一天算
     *
     * @param startUnixTime
     * @param endUnixTime
     * @return
     */
    public static int getBetweenDay(long startUnixTime, long endUnixTime) {

        int day = (int) ((endUnixTime - startUnixTime) / ONE_DAYS_MILLS);

        if ((endUnixTime - startUnixTime) % ONE_DAYS_MILLS != 0) {
            day += 1;
        }
        return day;
    }

    public static int getBetweenHours(long startUnixTime, long endUnixTime) {
        int hours = (int) ((endUnixTime - startUnixTime) / ONE_HOUR_MILLS);

        if ((endUnixTime - startUnixTime) % ONE_HOUR_MILLS != 0) {
            hours += 1;
        }
        return hours;
    }

    public static Long getTimestampWithoutSecond(Long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String format = sdf.format(new Date(timestamp));

        try {
            Date parse = sdf.parse(format);
            return parse.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 获取第几周
     *
     * @param date
     * @return
     */
    public static int getWeekNumberm(Date date) {
        LocalDate localDate = dateToLocalDate(date);
        WeekFields weekFields = WeekFields.ISO;
        int weekNumber = localDate.get(weekFields.weekOfWeekBasedYear());
        return weekNumber;
    }

    /**
     * 获取第几月
     *
     * @param date
     * @return
     */
    public static int getMonthNumberm(Date date) {
        return dateToLocalDate(date).getMonthValue();
    }

    /**
     * 获取今年第几天
     *
     * @param date
     * @return
     */
    public static int getDayOfYear(Date date) {
        return dateToLocalDate(date).getDayOfYear();
    }

    /**
     * 获取几个月前的第一天
     *
     * @param offset
     * @return
     */
    public static Date getFirstDateOfMinusByMonth(int offset, String format) {
        String firstDay = DateUtil.dateToString(DateUtil.getFirstDateOfMonth(DateUtil.minusByMonth(new Date(), offset)), DateUtil.YYYYMMDD2);
        return DateUtil.dayToDate(firstDay, format);
    }

    /**
     * 获取几个月前的月份
     *
     * @param offset
     * @return
     */
    public static int getMonthMinusByMonth(Date date, int offset) {
        int month = dateToLocalDateTime(date).minusMonths(offset).getMonthValue();
        return month;
    }

    /**
     * 获取指定格式的前几天
     *
     * @param offset
     * @param format
     * @return
     */
    public static Date getMinByDayToFormatDate(int offset, String format) {
        String minDay = DateUtil.dateToString(DateUtil.minusByDay(new Date(), offset), DateUtil.YYYYMMDD2);
        return DateUtil.dayToDate(minDay, format);
    }

    public static Integer getRemainSecondsOneDay(Date currentDate) {
        // 使用plusDays加传入的时间加1天，将时分秒设置成0
        LocalDateTime midnight = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault()).plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault());
        // 使用ChronoUnit.SECONDS.between方法，传入两个LocalDateTime对象即可得到相差的秒数
        long seconds = ChronoUnit.SECONDS.between(currentDateTime, midnight);
        return (int) seconds;
    }

    /**
     * 两个日期相差天数
     *
     * @param date1
     * @param date2
     * @return
     */
    public static long dayBetween(Date date1, Date date2) {
        if (Objects.isNull(date1) || Objects.isNull(date2)) {
            return 0;
        }
        return (getStartOfDay(date1).getTime() - getStartOfDay(date2).getTime()) / (24 * 60 * 60 * 1000);
    }

    /**
     * 两个日期较大的日期
     *
     * @param date1
     * @param date2
     * @return
     */
    public static Date max(Date date1, Date date2) {
        if (date1 == null) {
            return date2;
        }
        if (date2 == null) {
            return date1;
        }
        return date1.after(date2) ? date1 : date2;
    }
    
    public static void main(String[] args) {
        // System.out.println(DateUtil.YYYYMMDD);
        // System.out.println(DateUtil.minusByDay(new Date(), 360 * 100));
        // System.out.println(getBetweenDay(1739440206146L, System.currentTimeMillis()));
        // 2025-02-23 17:00:00
        // System.out.println(getBetweenHours(1740301200000L, System.currentTimeMillis()));
        // 2025-02-23 18:00:00
        // System.out.println(getBetweenHours(1740304800000L, System.currentTimeMillis()));
        Date minDate = DateUtil.dayToDate("2025-02-01", DateUtil.YYYYMMDD_STR);
        Date publishTime = DateUtil.plusByDay(minDate, ThreadLocalRandom.current().nextInt(28));
        publishTime = DateUtil.plusByHours(publishTime, ThreadLocalRandom.current().nextInt(6, 24));
        publishTime = DateUtil.plusBySeconds(publishTime, ThreadLocalRandom.current().nextInt(60*60));
        System.out.println(publishTime);
    }

}
