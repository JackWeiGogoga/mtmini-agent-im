import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Input, Segmented, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import { ReloadOutlined, SearchOutlined, ShopOutlined } from "@ant-design/icons";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { getUserBehaviorAnalytics } from "@/services/user-behavior-analytics-api";
import type {
  UserBehaviorAnalyticsQuery,
  UserBehaviorAnalyticsRow,
  UserBehaviorStatsRange,
} from "@/types";
import { getResponsiveControlStyle } from "@/utils/responsive";
import { useResponsive } from "@/hooks";

const { Text } = Typography;

const RANGE_OPTIONS: { label: string; value: UserBehaviorStatsRange }[] = [
  { label: "今日", value: "TODAY" },
  { label: "近7天", value: "LAST_7_DAYS" },
  { label: "近30天", value: "LAST_30_DAYS" },
];

const STAT_COLUMNS: {
  title: string;
  dataIndex: keyof UserBehaviorAnalyticsRow;
  width?: number;
}[] = [
  { title: "H5 应用打开数", dataIndex: "h5AppOpenCount" },
  { title: "H5 首页访问数", dataIndex: "h5HomePageViewCount" },
  { title: "H5 日志页访问数", dataIndex: "h5LogPageViewCount" },
  { title: "H5 算力页访问数", dataIndex: "h5PowerPageViewCount" },
  { title: "H5 知识库访问数", dataIndex: "h5KnowledgePageViewCount", width: 160 },
  { title: "H5 客服页访问数", dataIndex: "h5SupportPageViewCount" },
  { title: "客服入口点击数", dataIndex: "h5SupportEntryClickCount" },
  { title: "续费/活动点击数", dataIndex: "h5ExternalLinkClickCount" },
  { title: "有效充值提交数", dataIndex: "h5RechargeSubmitClickCount" },
  { title: "兑换码应用成功数", dataIndex: "h5RedemptionApplySuccessCount", width: 170 },
  { title: "日志详情展开数", dataIndex: "h5LogDetailExpandCount" },
  { title: "知识库关键操作数", dataIndex: "h5KnowledgeActionCount", width: 170 },
  { title: "PC 回复日志查看数", dataIndex: "pcCommentLogViewCount", width: 170 },
  { title: "PC 知识库查看数", dataIndex: "pcKnowledgeViewCount" },
  { title: "H5 自动回复成功变更数", dataIndex: "h5AutoReplyChangeCount", width: 190 },
  { title: "H5 差评处理成功变更数", dataIndex: "h5NegativeAppealChangeCount", width: 190 },
  { title: "PC 自动回复成功变更数", dataIndex: "pcAutoReplyChangeCount", width: 190 },
  { title: "PC 差评处理成功变更数", dataIndex: "pcNegativeAppealChangeCount", width: 190 },
  { title: "系统自动关停数", dataIndex: "systemAutoDisableCount" },
];

const UserBehaviorAnalytics: React.FC = () => {
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statsRange, setStatsRange] = useState<UserBehaviorStatsRange>("TODAY");
  const [sortField, setSortField] =
    useState<keyof UserBehaviorAnalyticsRow>();
  const [sortOrder, setSortOrder] = useState<SortOrder>(null);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<UserBehaviorAnalyticsRow[]>([]);
  const [total, setTotal] = useState(0);

  const queryParams = useMemo<UserBehaviorAnalyticsQuery>(
    () => ({
      keyword: keyword || undefined,
      statsRange,
      sortField,
      sortOrder,
      pageNum: current,
      pageSize,
    }),
    [current, keyword, pageSize, sortField, sortOrder, statsRange],
  );

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getUserBehaviorAnalytics(queryParams);
      setRecords(response.list || []);
      setTotal(response.total || 0);
    } catch (error) {
      console.error("Failed to load user behavior analytics:", error);
    } finally {
      setLoading(false);
    }
  }, [queryParams]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const columns = useMemo<ColumnsType<UserBehaviorAnalyticsRow>>(
    () => [
      {
        title: "商家名称",
        dataIndex: "shopName",
        key: "shopName",
        width: 240,
        fixed: isCompact ? undefined : "left",
        sorter: true,
        sortOrder: sortField === "shopName" ? sortOrder : null,
        render: (_: string | undefined, record) => (
          <Space align="start" size={8}>
            <ShopOutlined style={{ marginTop: 3 }} />
            <Space direction="vertical" size={2}>
              {record.shopName ? (
                <Text strong>{record.shopName}</Text>
              ) : (
                <Text type="secondary">未命名商家</Text>
              )}
              <Text type="secondary">{record.poiId || record.shopId}</Text>
            </Space>
          </Space>
        ),
      },
      {
        title: "商家手机号",
        dataIndex: "shopPhone",
        key: "shopPhone",
        width: 150,
        sorter: true,
        sortOrder: sortField === "shopPhone" ? sortOrder : null,
        render: (value?: string) => value || "-",
      },
      ...STAT_COLUMNS.map((column) => ({
        title: column.title,
        dataIndex: column.dataIndex,
        key: column.dataIndex,
        width: column.width || 140,
        align: "right" as const,
        sorter: true,
        sortOrder: sortField === column.dataIndex ? sortOrder : null,
        render: (value?: number) => Number(value || 0).toLocaleString(),
      })),
    ],
    [isCompact, sortField, sortOrder],
  );

  const handleSearch = () => {
    setCurrent(1);
    setKeyword(keywordInput.trim());
  };

  const handleReset = () => {
    setKeywordInput("");
    setKeyword("");
    setStatsRange("TODAY");
    setSortField(undefined);
    setSortOrder(null);
    setCurrent(1);
  };

  return (
    <PageContainer>
      <div className={pageStyles.toolbar}>
        <Space wrap>
          <Input
            allowClear
            value={keywordInput}
            prefix={<SearchOutlined />}
            placeholder="搜索商家名称、ID或手机号"
            style={getResponsiveControlStyle(isCompact, 300)}
            onChange={(event) => setKeywordInput(event.target.value)}
            onPressEnter={handleSearch}
          />
          <Button type="primary" onClick={handleSearch}>
            搜索
          </Button>
          <Button onClick={handleReset}>重置</Button>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => void loadData()}
          >
            刷新
          </Button>
        </Space>
        <Segmented<UserBehaviorStatsRange>
          value={statsRange}
          options={RANGE_OPTIONS}
          onChange={(value) => {
            setCurrent(1);
            setStatsRange(value);
          }}
        />
      </div>

      <Table<UserBehaviorAnalyticsRow>
        rowKey="shopId"
        loading={loading}
        columns={columns}
        dataSource={records}
        size={isCompact ? "small" : "middle"}
        scroll={{ x: 3880 }}
        pagination={{
          current,
          pageSize,
          total,
          showQuickJumper: !isCompact,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (page, size) => {
            setCurrent(page);
            setPageSize(size);
          },
        }}
        onChange={(_, __, sorter, extra) => {
          if (extra.action !== "sort") {
            return;
          }
          const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
          setCurrent(1);
          setSortField(
            (activeSorter?.columnKey as keyof UserBehaviorAnalyticsRow) ||
              undefined,
          );
          setSortOrder(activeSorter?.order || null);
        }}
      />
    </PageContainer>
  );
};

export default UserBehaviorAnalytics;
