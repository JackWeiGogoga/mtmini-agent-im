import request from "@/utils/request";
import type {
  ImBroadcastRecord,
  ImBroadcastRecordStatus,
  ImBroadcastTask,
  ImBroadcastTaskQuery,
  PageInfo,
} from "@/types";

const BASE_URL = "/system/im-broadcasts";

export async function getImBroadcastTasks(params: ImBroadcastTaskQuery): Promise<PageInfo<ImBroadcastTask>> {
  const response = await request.get<unknown, { data: PageInfo<ImBroadcastTask> }>(`${BASE_URL}/tasks`, { params });
  return response.data;
}

export async function getImBroadcastRecords(
  taskId: number,
  params?: { status?: ImBroadcastRecordStatus; pageNum?: number; pageSize?: number },
): Promise<PageInfo<ImBroadcastRecord>> {
  const response = await request.get<unknown, { data: PageInfo<ImBroadcastRecord> }>(
    `${BASE_URL}/tasks/${taskId}/records`, { params },
  );
  return response.data;
}
