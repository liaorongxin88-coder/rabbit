export type FarmStatus = "ENABLED" | "SUSPENDED" | "ORPHANED";
export type UserStatus = "ENABLED" | "DISABLED";
export type MembershipStatus = "ENABLED" | "DISABLED";

export type AdminRole = "SUPER_ADMIN" | "ADMIN";

export interface ApiResponse<T> {
 code: number;
 message: string;
 data: T;
}

export interface PageResult<T> {
 items: T[];
 total: number;
 page: number;
 pageSize: number;
}

export interface AdminSession {
 token: string;
 adminId: number;
 userName: string;
 role: AdminRole;
 permissions?: string[];
}

export interface WorkspaceSession {
 token: string;
 userId: number;
 userName: string;
 phoneBound: boolean;
 maskedPhone: string | null;
 hasPassword: boolean;
 canCreateHouse?: boolean;
 permissions?: string[];
}

export interface WorkspaceUserProfile {
 userId: number;
 userName: string;
 /** 账号：自己看得见、可以报给别人拉自己进兔舍的唯一标识。老后端不返回时为空。 */
 userCode?: string;
 openidBound: boolean;
 phoneBound: boolean;
 maskedPhone: string | null;
 hasPassword: boolean;
 permissions?: string[];
 createTime?: string | null;
 updateTime?: string | null;
}

export interface SmsCodeDelivery {
 expiresInSeconds: number;
 retryAfterSeconds: number;
}

export type HouseRole = "OWNER" | "MANAGER" | "STAFF" | "VIEWER";

export interface HouseInvitationRequest {
 /** 手机号或账号，服务端自己识别。 */
 identifier: string;
 /** 老后端只认 phone；识别成手机号时一并带上，保证向后兼容。 */
 phone?: string;
 role: HouseRole;
 requestId: string;
}

export interface HouseInvitationResult {
 /** JOINED：按账号邀请，对方当场入伙；SUBMITTED：手机号邀请，等对方登录。 */
 status: "JOINED" | "SUBMITTED";
 role: HouseRole;
}

export interface HousePermission {
 perms: "view" | "edit" | "control";
 role: HouseRole;
 isAdmin: boolean;
 permissions?: string[];
}

export interface HouseMember {
 userId: number;
 userName: string;
 phoneMasked?: string | null;
 role: HouseRole;
 perms: "view" | "edit" | "control";
 isAdmin: boolean;
 status?: MembershipStatus;
 joinTime?: string | null;
}

export interface AdminAccount {
 id: number;
 userName: string;
 role: AdminRole;
 enabled: boolean;
 lastLoginTime?: string | null;
 createTime?: string | null;
 updateTime?: string | null;
}

export interface BusinessUser {
 userId: number;
 userName: string;
 phoneBound: boolean;
 phoneMasked?: string | null;
 enabled: boolean;
 status?: UserStatus;
 houseCount: number;
 lastLoginTime?: string | null;
 createTime?: string | null;
 updateTime?: string | null;
}

export interface AdminFarm {
 id: number;
 name: string;
 status: FarmStatus;
 ownerNames?: string[];
 ownerCount?: number;
 memberCount?: number;
 cageCount?: number;
 rabbitCount?: number;
 layoutRows?: number;
 layoutCols?: number;
 layoutLayers?: number;
 remark?: string | null;
 createTime?: string | null;
 updateTime?: string | null;
}

export interface CreateAdminFarmRequest {
 name: string;
 layoutRows: number;
 layoutCols: number;
 layoutLayers: number;
 remark?: string;
 ownerUserId?: number;
 ownerPhone?: string;
 requestId: string;
}

export interface UpdateAdminFarmRequest {
 name: string;
 remark?: string;
}

export interface AdminFarmMember {
 userId: number;
 userName: string;
 phoneMasked?: string | null;
 role: HouseRole;
 status?: MembershipStatus;
 joinTime?: string | null;
}

export interface RabbitHouse {
 id: number;
 name: string;
 layoutRows: number;
 layoutCols: number;
 layoutLayers: number;
 remark?: string | null;
 createTime?: string | null;
 updateTime?: string | null;
}

export interface Cage {
 id: number;
 houseId: number;
 cageNumber: string;
 rowCode?: string | null;
 layerIndex?: number | null;
 positionIndex?: number | null;
 status?: string | null;
 rabbitCount: number;
 breedingOccupantGender?: string | null;
 isFed: boolean;
 isEnabled: boolean;
 remark?: string | null;
}

export interface Rabbit {
 id: number;
 houseId: number;
 cageId: number;
 motherId?: number | null;
 type: string;
 gender: string;
 breed?: string | null;
 arrivalMethod?: string | null;
 arrivalDate?: string | null;
 weight?: number | null;
 growthStage?: string | null;
 reproductiveStage?: string | null;
 /**
  * 生产阶段投影。种母兔的阶段由生产流程状态机维护，是唯一权威口径；
  * `reproductiveStage` 是旧词汇，仅对非种母兔仍有意义。
  */
 currentStage?: string | null;
 currentCycleId?: number | null;
 stageEnteredAt?: string | null;
 lastMatingDate?: string | null;
 stateVersion?: number | null;
 isActive: boolean;
 isQuarantined: boolean;
 quarantineReason?: string | null;
 departureDate?: string | null;
 departureReason?: string | null;
 createTime?: string | null;
}

export interface RangeRabbitEntrySkippedCage {
 cageId: number;
 cageNumber: string;
 reason: string;
}

export interface RangeRabbitEntryResult {
 requestedSlotCount: number;
 missingCageCount: number;
 unplacedCageCount: number;
 enteredCageCount: number;
 enteredRabbitCount: number;
 replayedCageCount: number;
 skippedCages: RangeRabbitEntrySkippedCage[];
}

export interface RabbitReplacementRequest {
 rabbitIds: number[];
 forceExitBatch: boolean;
 targetCageId: number;
 requestId: string;
}

export interface RabbitReplacementItem {
 rabbitId: number;
 replacementRecordId: number;
 targetCageId: number;
}

export interface RabbitReplacementResult {
 items: RabbitReplacementItem[];
}

export interface DashboardSummary {
 selectedHouseId?: number | null;
 houseCount: number;
 year: number;
 totalRabbits: number;
 seedRabbits: number;
 maleRabbits: number;
 femaleRabbits: number;
 bredRabbits: number;
 readyForBreeding: number;
 litters: number;
 nursingKits: number;
 commodityRabbits: number;
 replacementRabbits: number;
 liveRate: number;
 monthlyBirths: number[];
 monthlyWeaned: number[];
}

export interface ProductionBatch {
 id: number;
 houseId: number;
 batchCode: string;
 status: string;
 startDate?: string | null;
 endDate?: string | null;
 remark?: string | null;
 createTime?: string | null;
}

export interface PendingWeaningRecord {
 id: number;
 batchId: number;
 breedingCycleId?: number | null;
 rabbitId: number;
 weaningDate: string | number;
 weaningCount: number;
 waitingCount: number;
 maleCount?: number | null;
 femaleCount?: number | null;
 avgWeight?: number | null;
 allocSummary?: string | null;
}

export interface WeaningSeparationRequest {
 allocations: Array<{ cageId: number; count: number }>;
 requestId: string;
}

export interface WeaningSeparationResult {
 weaningRecordId: number;
 separatedCount: number;
 waitingCount: number;
 generatedRabbitIds: number[];
 replayed: boolean;
}

export interface BatchRabbit {
 id: number;
 batchId: number;
 rabbitId: number;
 maleRabbitId?: number | null;
 latestCycleId?: number | null;
 currentNursingKits?: number | null;
 nursingLitterCount?: number | null;
 batchRole?: string | null;
 currentStatus?: string | null;
 nextEventDate?: string | null;
 nextEventType?: string | null;
 isActive: boolean;
 rabbitType?: string | null;
 rabbitGender?: string | null;
 cageId?: number | null;
}

/** 一次生产动作的结果。 */
export interface ReproActionResult {
 cycleId: number;
 eventId?: number | null;
 litterId?: number | null;
 nextTaskId?: number | null;
 stage?: string | null;
 lifecycle?: string | null;
 nextDueTime?: string | number | null;
 /** 关周期并自动接续时，新开出来的周期。 */
 followUpCycleId?: number | null;
 /** 命中幂等回放：本次没有产生新的状态变更。 */
 replayed?: boolean;
}

export interface ReproBulkItem {
 ok: boolean;
 taskId?: number | null;
 cycleId?: number | null;
 rabbitId?: number | null;
 code?: number | null;
 message?: string | null;
 replayed?: boolean;
}

export interface ReproBulkResult {
 total: number;
 succeeded: number;
 failed: number;
 items: ReproBulkItem[];
}

/** 一条生产待办。 */
export interface ReproTask {
 id: number;
 taskType: string;
 /** 服务端给的中文标签，客户端不再自己拼。 */
 taskLabel: string;
 /** 该待办对应的自然动作；为空表示不能直接推进生产流程。 */
 action?: string | null;
 subjectType?: string | null;
 subjectId?: number | null;
 cycleId?: number | null;
 rabbitId?: number | null;
 batchId?: number | null;
 cageId?: number | null;
 dueDate?: string | number | null;
 dueTime?: string | number | null;
 status?: string | null;
 /** 是否逾期。由服务端判定，避免前后端时区不一致。 */
 overdue?: boolean;
 snoozeCount?: number;
 /** 服务端下发的待办说明。 */
 remark?: string | null;
}

export interface ReproTaskPage {
 total: number;
 page: number;
 size: number;
 items: ReproTask[];
}

export interface BreedingCycle {
 id: number;
 houseId: number;
 batchId: number;
 motherRabbitId: number;
 maleRabbitId?: number | null;
 cycleNo: number;
 status: string;
 /** doe-breeding-v2 的权威阶段；status 是待删除的旧中文快照。 */
 stage?: string | null;
 lifecycle?: string | null;
 result?: string | null;
 matingDate?: string | null;
 pregnancyCheckDate?: string | null;
 pregnancyResult?: string | null;
 birthDate?: string | null;
 currentNursingKits?: number | null;
 weanedKits?: number | null;
 nextEventDate?: string | null;
 nextEventType?: string | null;
 closedAt?: string | null;
 closeReason?: string | null;
}

export interface ImageCaptcha {
  captchaId: string;
  imageBase64: string;
  expiresInSeconds: number;
}

export type RabbitDepartureType = "cull" | "death";

export interface RabbitDepartureRequest {
 rabbitId: number;
 eventType: RabbitDepartureType;
 actionDate: number;
 reason: string;
 remark?: string;
 forceExitBatch: true;
 requestId: string;
}

export type OutboundEligibility =
 | "NORMAL"
 | "EARLY_SALE"
 | "NEEDS_ACTION"
 | "BLOCKED";

export interface OutboundSummary {
 normal: number;
 earlySale: number;
 needsAction: number;
 blocked: number;
}

export interface OutboundRabbit {
 rabbitId: number;
 cageId?: number | null;
 cageNumber: string;
 rowCode: string;
 layerIndex?: number | null;
 positionIndex?: number | null;
 rabbitType: string;
 gender: string;
 weight?: number | null;
 stage: string;
 batchId?: number | null;
 stateVersion: number;
 eligibility: OutboundEligibility;
 reasonCode: string;
 message: string;
 recommendedAction: string;
 defaultSelected: boolean;
}

export interface OutboundSelectedItem {
 rabbitId: number;
 stateVersion: number;
 selectionType: "NORMAL" | "EARLY_SALE";
 earlySaleReason?: string | null;
}

export interface OutboundTask {
 taskId: string;
 houseId: number;
 entryType: "RABBIT" | "CAGE" | "ROW" | "HOUSE";
 status: "SELECTING" | "WAITING_CONFIRMATION" | "COMPLETED" | "CANCELLED";
 revision: number;
 saleTime?: string | null;
 totalWeight?: number | null;
 unitPrice?: number | null;
 customer?: string | null;
 remark?: string | null;
 saleOrderId?: number | null;
 resumed: boolean;
 summary: OutboundSummary;
 rabbits: OutboundRabbit[];
 selectedItems: OutboundSelectedItem[];
}

export interface OutboundConflict {
 rabbitId: number;
 errorCode: string;
 currentState: string;
 message: string;
 recommendedAction: string;
}

export interface OutboundSubmitResult {
 status: "COMPLETED" | "CONFLICT" | "FAILED" | "PROCESSING";
 requestId: string;
 taskId: string;
 saleOrderId?: number | null;
 saleOrderNumber?: string | null;
 saleTime?: string | null;
 rabbitCount: number;
 cageCount: number;
 rowCount: number;
 totalWeight?: number | null;
 totalAmount?: number | null;
 errorCode?: string | null;
 message?: string | null;
 conflicts: OutboundConflict[];
}

export interface AuditLog {
 id: number;
 traceId?: string | null;
 userId?: number | null;
 houseId?: number | null;
 method?: string | null;
 path?: string | null;
 status?: number | null;
 apiCode?: number | null;
 apiMessage?: string | null;
 costMs?: number | null;
 createTime?: string | null;
}

export interface FarmOverview {
 farm: AdminFarm;
 memberCount: number;
 cageCount: number;
 rabbitCount: number;
 batchCount: number;
 members: AdminFarmMember[];
 recentAuditLogs: AuditLog[];
}
