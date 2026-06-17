# 查询mt_promotion_setting推广配置修改记录

```shell
mysqlbinlog \
  --base64-output=DECODE-ROWS -vv \
  --start-datetime="2026-05-08 00:00:00" \
  --stop-datetime="2026-05-08 23:59:59" \
  /var/lib/mysql/binlog.000003 \
  | awk '
      /Table_map: .*`mt_promotion_setting` mapped to number/ {in_table=1; buf=$0 "\n"; next}
      in_table {buf = buf $0 "\n"}
      in_table && /COMMIT/ {
        if (buf ~ /@2=408([^0-9]|$)/ || buf ~ /shop_id.*408/) print buf "\n-----";
        in_table=0; buf="";
      }'
```

# mt_access_token 修改记录

```
mysqlbinlog \
  --base64-output=DECODE-ROWS -vv \
  --start-datetime="2026-05-08 00:00:00" \
  --stop-datetime="2026-05-08 23:59:59" \
  /var/lib/mysql/binlog.000003 \
  | awk '
      /Table_map: .*`mt_access_token` mapped to number/ {in_table=1; buf=$0 "\n"; next}
      in_table {buf = buf $0 "\n"}
      in_table && /COMMIT/ {
        if (buf ~ /@1=249([^0-9]|$)/) print buf "\n-----";
        in_table=0; buf="";
      }'

```
