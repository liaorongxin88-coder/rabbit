import {
  workspaceDeleteJson,
  workspaceGetJson,
  workspacePostJson,
  workspacePutJson,
} from "@/lib/request";
import {
  batchActionPath,
  pendingWeaningRecordsPath,
  rabbitEventPath,
  weaningSeparationPath,
} from "@/lib/batch-workflow";
import { rabbitReplacementPath } from "@/lib/rabbit-replacement";
import type {
  BatchRabbit,
  BatchRabbitEntryResult,
  BreedingCycle,
  Cage,
  DashboardSummary,
  HouseInvitationRequest,
  HouseInvitationResult,
  HouseMember,
  HousePermission,
  OutboundSelectedItem,
  OutboundSubmitResult,
  OutboundTask,
  PendingWeaningRecord,
  ProductionBatch,
  Rabbit,
  RabbitDepartureRequest,
  RabbitHouse,
  RabbitReplacementRequest,
  RabbitReplacementResult,
  RangeRabbitEntryResult,
  ReproActionResult,
  ReproBulkResult,
  ReproTaskPage,
  WeaningSeparationRequest,
  WeaningSeparationResult,
  WorkspaceSession,
  WorkspaceUserProfile,
  ImageCaptcha,
  SmsCodeDelivery,
} from "@/types/api";
import type { RabbitSaleRequest } from "@/types/rabbit-sale";
import type { VaccinationRecord } from "@/types/rabbit-vaccination";

export function requestId() {
  return crypto.randomUUID();
}

export function getWorkspaceImageCaptcha() {
  return workspaceGetJson<ImageCaptcha>("/api/auth/captcha");
}

export function loginWorkspace(data: {
  userName: string;
  password: string;
  captchaId: string;
  captchaCode: string;
}) {
  return workspacePostJson<WorkspaceSession>("/api/auth/login", data);
}

export function sendWorkspaceSmsCode(phone: string, purpose: string) {
  return workspacePostJson<SmsCodeDelivery>("/api/auth/sms/code", {
    phone,
    purpose,
  });
}

export function resetWorkspacePasswordBySms(data: {
  phone: string;
  code: string;
  newPassword: string;
}) {
  return workspacePostJson<void>("/api/auth/sms/reset-password", data);
}

export function getWorkspaceProfile() {
  return workspaceGetJson<WorkspaceUserProfile>("/api/auth/me");
}

export function updateWorkspaceUserName(userName: string) {
  return workspacePutJson<WorkspaceUserProfile>("/api/auth/me", { userName });
}

export function updateWorkspacePassword(data: {
  oldPassword?: string;
  newPassword: string;
}) {
  return workspacePutJson<void>("/api/auth/password", data);
}

export function updateWorkspacePhone(data: {
  phone: string;
  code: string;
  currentPassword?: string;
  currentPhone?: string;
  currentPhoneCode?: string;
}) {
  return workspacePutJson<WorkspaceUserProfile>("/api/auth/phone", data);
}

export function listWorkspaceHouses() {
  return workspaceGetJson<RabbitHouse[]>("/api/houses");
}

export function getHousePermission(houseId: number) {
  return workspaceGetJson<HousePermission>("/api/houses/permission", {
    houseId,
  });
}

export function createWorkspaceHouse(data: {
  name: string;
  layoutRows: number;
  layoutCols: number;
  layoutLayers: number;
  remark?: string;
}) {
  return workspacePostJson<RabbitHouse>("/api/houses", {
    ...data,
    requestId: requestId(),
  });
}

export function updateWorkspaceHouse(
  houseId: number,
  data: { name: string; remark?: string },
) {
  return workspacePutJson<RabbitHouse>(`/api/houses/${houseId}`, data, {
    houseId,
  });
}

export function deleteWorkspaceHouse(houseId: number) {
  return workspaceDeleteJson<void>(`/api/houses/${houseId}`, { houseId });
}

export function getDashboard(
  houseId?: number | null,
  year = new Date().getFullYear(),
) {
  return workspaceGetJson<DashboardSummary>("/api/reports/dashboard", {
    params: { houseId: houseId ?? undefined, year },
  });
}

export function listCages(houseId: number) {
  return workspaceGetJson<Cage[]>("/api/cages", { houseId });
}

/** cageNumber 留空时由后端按「排-位-层」生成，与建兔舍自动铺的笼位同一套规则。 */
export function createCage(
  houseId: number,
  data: {
    cageNumber?: string;
    rowCode?: string;
    layerIndex?: number;
    positionIndex?: number;
    remark?: string;
    isEnabled: boolean;
  },
) {
  return workspacePostJson<Cage>("/api/cages", data, { houseId });
}

export function updateCage(
  houseId: number,
  cageId: number,
  data: {
    cageNumber: string;
    rowCode?: string;
    layerIndex?: number;
    positionIndex?: number;
    remark?: string;
    isEnabled: boolean;
  },
) {
  return workspacePutJson<Cage>(`/api/cages/${cageId}`, data, { houseId });
}

export function deleteCage(houseId: number, cageId: number) {
  return workspaceDeleteJson<void>(`/api/cages/${cageId}`, { houseId });
}

export function listRabbits(houseId: number) {
  return workspaceGetJson<Rabbit[]>("/api/rabbits", { houseId });
}

export function getRabbit(houseId: number, rabbitId: number) {
  return workspaceGetJson<Rabbit>(`/api/rabbits/${rabbitId}`, { houseId });
}

export function retainRabbitsAsReplacement(
  houseId: number,
  data: RabbitReplacementRequest,
) {
  return workspacePostJson<RabbitReplacementResult>(
    rabbitReplacementPath(),
    data,
    { houseId },
  );
}

export function createRabbitSale(houseId: number, data: RabbitSaleRequest) {
  return workspacePostJson<void>("/api/sales", data, { houseId });
}

export function promoteReplacementRabbit(
  houseId: number,
  rabbitId: number,
  promotionRequestId: string,
) {
  return workspacePostJson<void>(
    `/api/rabbits/${rabbitId}/promote-breeder`,
    { requestId: promotionRequestId },
    { houseId },
  );
}

export interface RabbitWriteInput {
  cageId: number;
  motherId?: number;
  type?: string;
  gender?: string;
  breed?: string;
  arrivalMethod?: string;
  sourceSeller?: string;
  arrivalDate?: string;
  weight?: number;
  growthStage?: string;
  reproductiveStage?: string;
}

/**
 * 录入时直接入轨的生产阶段。
 *
 * 只对种母兔成立，且只能在创建时传：建兔与开周期必须同事务，否则就会出现
 * “存栏里有这只母兔、待办里没有”的黑洞。修改已有母兔的阶段走生产动作，不走这里。
 */
export interface RabbitReproEntryInput {
  reproStage?: string;
  batchId?: number;
  stageEnteredAt?: string;
  matingDate?: string;
  birthDate?: string;
  liveKits?: number;
}

export function createRabbit(
  houseId: number,
  data: RabbitWriteInput & RabbitReproEntryInput,
) {
  return workspacePostJson<Rabbit>(
    "/api/rabbits",
    { ...data, requestId: requestId() },
    { houseId },
  );
}

export interface BatchRabbitEntryInput extends RabbitWriteInput {
  type: string;
  gender: string;
  arrivalMethod: string;
  arrivalDate: string;
  quantity: number;
  totalWeight: number;
}

export function createRabbitBatch(
  houseId: number,
  data: BatchRabbitEntryInput,
) {
  return workspacePostJson<BatchRabbitEntryResult>(
    "/api/rabbits/batch-entry",
    { ...data, requestId: requestId() },
    { houseId },
  );
}

export function createRabbitsInRange(
  houseId: number,
  data: Omit<RabbitWriteInput, "cageId"> & {
    rowStart: number;
    rowEnd: number;
    positionStart: number;
    positionEnd: number;
    layerStart: number;
    layerEnd: number;
    rabbitsPerCage: number;
  },
) {
  return workspacePostJson<RangeRabbitEntryResult>(
    "/api/rabbits/range-entry",
    { ...data, requestId: requestId() },
    { houseId },
  );
}

export function updateRabbit(
  houseId: number,
  rabbitId: number,
  data: RabbitWriteInput,
) {
  const { type: _type, gender: _gender, ...update } = data;
  return workspacePutJson<Rabbit>(
    `/api/rabbits/${rabbitId}`,
    { ...update, requestId: requestId() },
    { houseId },
  );
}

/**
 * 换笼位结果。mode 里的三种结局对用户是完全不同的事实，提示文案必须区分。
 */
export interface CageTransferResult {
  mode: "MOVE" | "APPEND" | "SWAP" | "REPLAY";
  rabbitId: number;
  fromCageId: number | null;
  toCageId: number | null;
  swappedRabbitId: number | null;
}

/**
 * 换笼位。与在编辑表单里改笼位不同：目标笼已有种兔时这里会执行两笼对调，
 * 而编辑路径只会报“该繁殖笼已有在栏种兔”。
 */
export function transferRabbitCage(
  houseId: number,
  rabbitId: number,
  targetCageId: number,
) {
  return workspacePostJson<CageTransferResult>(
    `/api/rabbits/${rabbitId}/cage-transfer`,
    { targetCageId, requestId: requestId() },
    { houseId },
  );
}

export function listBatches(houseId: number) {
  return workspaceGetJson<ProductionBatch[]>("/api/batches", { houseId });
}

export function createBatch(
  houseId: number,
  data: { batchCode: string; femaleRabbitIds: number[]; remark?: string },
) {
  return workspacePostJson<ProductionBatch>(
    "/api/batches",
    { ...data, requestId: requestId() },
    { houseId },
  );
}

/** 改批次编号。批次建完才发现名字打错时，不必重建批次搬兔只。 */
export function renameBatch(
  houseId: number,
  batchId: number,
  batchCode: string,
) {
  return workspacePostJson<ProductionBatch>(
    `/api/batches/${batchId}/code`,
    { batchCode, requestId: requestId() },
    { houseId },
  );
}

export function listBatchRabbits(houseId: number, batchId: number) {
  return workspaceGetJson<BatchRabbit[]>(
    `/api/batches/${batchId}/batch-rabbits`,
    {
      houseId,
    },
  );
}

export function listBreedingCycles(houseId: number, batchId: number) {
  return workspaceGetJson<BreedingCycle[]>(
    `/api/batches/${batchId}/breeding-cycles`,
    {
      houseId,
    },
  );
}

export function listPendingWeaningRecords(houseId: number, batchId: number) {
  return workspaceGetJson<PendingWeaningRecord[]>(
    pendingWeaningRecordsPath(batchId),
    { houseId },
  );
}

export function separateWeaningRecord(
  houseId: number,
  batchId: number,
  weaningRecordId: number,
  data: WeaningSeparationRequest,
) {
  return workspacePostJson<WeaningSeparationResult>(
    weaningSeparationPath(batchId, weaningRecordId),
    data,
    { houseId },
  );
}

export function submitRabbitDeparture(
  houseId: number,
  data: RabbitDepartureRequest,
) {
  return workspacePostJson<void>(rabbitEventPath(), data, { houseId });
}

/**
 * 批次层面仅存的两个动作。
 *
 * doe-breeding-v2 之后，生产动作（催情/配种/摸胎/备产/接产/分笼）不再挂在批次上，
 * 而是挂在生产周期上，走 {@link submitReproAction}。批次现在只是个标签。
 */
export type BatchAction = "sale" | "complete";

/** 生产动作，与后端 ReproAction 一一对应。 */
export type ReproActionName =
  | "ESTRUS"
  | "MATING"
  | "PALPATION"
  | "PREPARTUM"
  | "DELIVERY"
  | "WEANING"
  | "ABORTION"
  | "POSTPONE"
  | "RETIRE";

/**
 * 单头母兔的一次状态推进——六个生产动作共用的唯一写入口。
 *
 * 动作是否合法由服务端的转换表判定，前端不再自己判断「当前阶段能不能做这个动作」；
 * 非法组合会返回 409 并附上可直接展示的中文原因。
 */
export function submitReproAction(
  houseId: number,
  cycleId: number,
  data: Record<string, unknown>,
  actionRequestId: string,
) {
  return workspacePostJson<ReproActionResult>(
    `/api/repro/cycles/${cycleId}/actions`,
    { ...data, requestId: actionRequestId },
    { houseId },
  );
}

/** 批量推进待办。部分成功是常态，整体仍返回 200，失败项在 items 里。 */
export function submitReproBulkAction(
  houseId: number,
  data: Record<string, unknown>,
) {
  return workspacePostJson<ReproBulkResult>(
    "/api/repro/tasks/bulk-actions",
    data,
    { houseId },
  );
}

/**
 * 阶段→可执行动作字典。
 *
 * 用于决定「流产」这类非计划入口该不该出现。规则的唯一来源是服务端
 * 转换表；前端拄写一份日后必定漂移，用户会看到点下去就 409 的选项。
 */
export function listReproStageActions(houseId: number) {
  return workspaceGetJson<
    {
      stage: string;
      stageLabel: string;
      actions: { action: ReproActionName; label: string }[];
    }[]
  >("/api/repro/stage-actions", { houseId });
}

/** 入轨阶段字典：每个可入轨阶段必须补录哪些事实，由服务端的 EntryPoint 表下发。 */
export interface ReproEntryPoint {
  stage: string;
  stageLabel: string;
  requiredFacts: { fact: string; label: string }[];
}

export function listReproEntryPoints(houseId: number) {
  return workspaceGetJson<ReproEntryPoint[]>("/api/repro/entry-points", {
    houseId,
  });
}

/** 待办清单。首页、笼位、兔卡、批次详情共用这一个接口，只是过滤条件不同。 */
export function listReproTasks(
  houseId: number,
  params: {
    batchId?: number;
    type?: string;
    cageId?: number;
    rabbitId?: number;
    dueBefore?: number;
    page?: number;
    size?: number;
  } = {},
) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) query.set(key, String(value));
  }
  const suffix = query.toString();
  return workspaceGetJson<ReproTaskPage>(
    `/api/tasks${suffix ? `?${suffix}` : ""}`,
    { houseId },
  );
}

export function submitBatchAction(
  houseId: number,
  batchId: number,
  action: BatchAction,
  data: Record<string, unknown>,
  actionRequestId: string,
) {
  return workspacePostJson<void>(
    batchActionPath(batchId, action),
    { ...data, requestId: actionRequestId },
    { houseId },
  );
}

export function createOutboundTask(houseId: number) {
  return workspacePostJson<OutboundTask>(
    "/api/outbound/tasks",
    { entryType: "HOUSE", resumeExisting: false },
    { houseId },
  );
}

export function precheckOutboundTask(houseId: number, taskId: string) {
  return workspacePostJson<OutboundTask>(
    `/api/outbound/tasks/${taskId}/precheck`,
    {},
    { houseId },
  );
}

export function saveOutboundTask(
  houseId: number,
  taskId: string,
  data: {
    revision: number;
    status: "SELECTING" | "WAITING_CONFIRMATION";
    items: OutboundSelectedItem[];
    saleTime: number;
    totalWeight: number;
    unitPrice?: number;
    customer?: string;
    remark?: string;
  },
) {
  return workspacePutJson<OutboundTask>(`/api/outbound/tasks/${taskId}`, data, {
    houseId,
  });
}

export function submitOutboundTask(
  houseId: number,
  taskId: string,
  data: {
    rabbitIds: number[];
    stateVersions: Record<string, number>;
    earlySaleReasons: Record<string, string>;
    saleTime: number;
    totalWeight: number;
    unitPrice?: number;
    customer?: string;
    remark?: string;
    requestId: string;
  },
) {
  return workspacePostJson<OutboundSubmitResult>(
    `/api/outbound/tasks/${taskId}/submit`,
    data,
    { houseId },
  );
}

export function cancelOutboundTask(houseId: number, taskId: string) {
  return workspacePostJson<void>(
    `/api/outbound/tasks/${taskId}/cancel`,
    {},
    { houseId },
  );
}

export function listHouseMembers(houseId: number) {
  return workspaceGetJson<HouseMember[]>("/api/house-members", { houseId });
}

export function createHouseInvitation(
  houseId: number,
  data: HouseInvitationRequest,
) {
  return workspacePostJson<HouseInvitationResult>(
    "/api/house-invitations",
    data,
    {
      houseId,
    },
  );
}

export function updateHouseMember(
  houseId: number,
  userId: number,
  role: HouseMember["role"],
) {
  return workspacePutJson<void>(
    `/api/house-members/${userId}`,
    { role, requestId: requestId() },
    { houseId },
  );
}

export function removeHouseMember(houseId: number, userId: number) {
  return workspaceDeleteJson<void>(`/api/house-members/${userId}`, {
    houseId,
    params: { requestId: requestId() },
  });
}

/**
 * 单只兔的接种历史。
 *
 * admin 只读不写：接种是站在笼前完成的现场动作，录入入口在 App，
 * 后台承担的是回查和核对。
 */
export function uploadWorkspaceImage(houseId: number, file: File) {
  const form = new FormData();
  form.set("file", file);
  return workspacePostJson<{ fileId: string }>(
    "/api/business-files/images",
    form,
    {
      houseId,
    },
  );
}

export function createWorkspaceAbnormalCondition(
  houseId: number,
  data: {
    rabbitId: number;
    warningStatus: string;
    imageFileId: string;
    remark: string;
    requestId: string;
  },
) {
  return workspacePostJson<void>("/api/abnormal", data, { houseId });
}

export function listRabbitVaccinations(houseId: number, rabbitId: number) {
  return workspaceGetJson<VaccinationRecord[]>("/api/vaccinations", {
    houseId,
    params: { rabbitId },
  });
}
