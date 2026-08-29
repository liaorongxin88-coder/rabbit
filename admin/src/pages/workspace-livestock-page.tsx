import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  Edit3Icon,
  EyeIcon,
  Grid2X2Icon,
  ListIcon,
  PlusIcon,
  RabbitIcon,
  RefreshCwIcon,
  SearchIcon,
  Trash2Icon,
  WarehouseIcon,
} from "lucide-react";
import { toast } from "sonner";
import {
  createCage,
  deleteCage,
  getBatchStatistics,
  listBatches,
  listCages,
  listPendingWeaningRecords,
  listRabbits,
  listReproEntryPoints,
  listReproStageActions,
  updateCage,
} from "@/api/workspace";
import type { ReproEntryPoint } from "@/api/workspace";
import { BatchStatisticsSummary } from "@/components/batch-statistics";
import { CageAttentionLegend, CageMap } from "@/components/cage-map";
import { PageHeader } from "@/components/page-header";
import { HousePermissionBadge } from "@/components/permission-badge";
import { RabbitFormDialog } from "@/components/rabbit-operation-dialogs";
import { RabbitRangeEntryDialog } from "@/components/rabbit-range-entry-dialog";
import { buildCageLayout, cageAcceptsMoreRabbits } from "@/lib/cage-map";
import { inProgressProductionBatches } from "@/lib/rabbit-repro-entry";
import { rabbitStageSummary, rabbitTypeLabel } from "@/lib/rabbits";
import { hasPermission, useWorkspace } from "@/lib/workspace";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Empty, EmptyDescription, EmptyTitle } from "@/components/ui/empty";
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import type {
  BatchStatistics,
  Cage,
  PendingWeaningRecord,
  ProductionBatch,
  Rabbit,
} from "@/types/api";

const cageStatusLabels: Record<string, string> = {
  "0": "空闲",
  "1": "种兔",
  "2": "后备兔",
  "3": "商品兔",
};

type BreedingCageFilter = "all" | "doe" | "buck";

/** 地图按排分页：一个几十排的兔场一次铺完会生成上千个格子。 */
const CAGE_ROW_BATCH = 6;

export function WorkspaceLivestockPage() {
  const workspace = useWorkspace();
  const [cages, setCages] = useState<Cage[]>([]);
  const [rabbits, setRabbits] = useState<Rabbit[]>([]);
  const [loading, setLoading] = useState(false);
  const [queryInput, setQueryInput] = useState("");
  const [query, setQuery] = useState("");
  const [breedingCageFilter, setBreedingCageFilter] =
    useState<BreedingCageFilter>("all");
  const [cageDialog, setCageDialog] = useState<{
    open: boolean;
    cage: Cage | null;
  }>({ open: false, cage: null });
  const [rabbitCreateOpen, setRabbitCreateOpen] = useState(false);
  const [rangeEntryOpen, setRangeEntryOpen] = useState(false);
  const [rabbitInitialCageId, setRabbitInitialCageId] = useState<number | null>(
    null,
  );
  const [deleteTarget, setDeleteTarget] = useState<Cage | null>(null);
  const [cageDetail, setCageDetail] = useState<Cage | null>(null);
  /** 笼位区默认看分层地图；列表保留，大兔场按编号翻找时更好用。 */
  const [cageView, setCageView] = useState<"map" | "list">("map");
  const [visibleRowCount, setVisibleRowCount] = useState(CAGE_ROW_BATCH);
  /** 阶段→中文名。服务端下发，客户端不再自带一张会漂移的对照表。 */
  const [reproStageLabels, setReproStageLabels] = useState<
    Record<string, string>
  >({});
  const [entryPoints, setEntryPoints] = useState<ReproEntryPoint[]>([]);
  const [batches, setBatches] = useState<ProductionBatch[]>([]);
  const [reproLoading, setReproLoading] = useState(false);
  const [reproLoadFailed, setReproLoadFailed] = useState(false);
  const [selectedBatchId, setSelectedBatchId] = useState("");
  const [batchStatistics, setBatchStatistics] =
    useState<BatchStatistics | null>(null);
  const [batchStatisticsStatus, setBatchStatisticsStatus] = useState<
    "idle" | "loading" | "ready" | "error"
  >("idle");
  const [pendingCommodityAllocationCount, setPendingCommodityAllocationCount] =
    useState<number | null>(null);
  const reproLoadVersion = useRef(0);
  const batchStatisticsLoadVersion = useRef(0);
  const canEdit = hasPermission(workspace.permission, "rabbit:rabbits:edit");
  const canControl = hasPermission(workspace.permission, "rabbit:cages:edit");
  const canReadRepro = hasPermission(
    workspace.permission,
    "rabbit:batches:query",
  );

  const load = useCallback(async () => {
    if (!workspace.selectedHouse) {
      setCages([]);
      setRabbits([]);
      return;
    }
    setLoading(true);
    try {
      const [nextCages, nextRabbits] = await Promise.all([
        listCages(workspace.selectedHouse.id),
        listRabbits(workspace.selectedHouse.id),
      ]);
      setCages(nextCages);
      setRabbits(nextRabbits);
    } catch {
      setCages([]);
      setRabbits([]);
    } finally {
      setLoading(false);
    }
  }, [workspace.selectedHouse]);

  const loadReproDictionaries = useCallback(async () => {
    const loadVersion = ++reproLoadVersion.current;
    batchStatisticsLoadVersion.current += 1;
    setReproStageLabels({});
    setEntryPoints([]);
    setBatches([]);
    setReproLoadFailed(false);
    setBatchStatistics(null);
    setBatchStatisticsStatus("idle");
    setPendingCommodityAllocationCount(null);
    if (!workspace.selectedHouse || !canReadRepro) {
      setReproLoading(false);
      return;
    }
    setReproLoading(true);
    const houseId = workspace.selectedHouse.id;
    try {
      const [stages, entries, nextBatches] = await Promise.all([
        listReproStageActions(houseId),
        listReproEntryPoints(houseId),
        listBatches(houseId),
      ]);
      if (loadVersion !== reproLoadVersion.current) return;
      setReproStageLabels(
        Object.fromEntries(stages.map((item) => [item.stage, item.stageLabel])),
      );
      setEntryPoints(entries);
      setBatches(nextBatches);
      try {
        const pendingRecords = await Promise.all(
          nextBatches.map((batch) =>
            listPendingWeaningRecords(houseId, batch.id),
          ),
        );
        if (loadVersion !== reproLoadVersion.current) return;
        setPendingCommodityAllocationCount(
          pendingRecords
            .flatMap((records: PendingWeaningRecord[]) => records)
            .reduce((total, record) => total + record.waitingCount, 0),
        );
      } catch {
        if (loadVersion !== reproLoadVersion.current) return;
        setPendingCommodityAllocationCount(null);
      }
    } catch {
      if (loadVersion !== reproLoadVersion.current) return;
      // 字典拿不到时退回英文枚举与旧阶段字段，列表不应该因此变成空页。
      setReproStageLabels({});
      setEntryPoints([]);
      setBatches([]);
      setReproLoadFailed(true);
      setPendingCommodityAllocationCount(null);
    } finally {
      if (loadVersion === reproLoadVersion.current) {
        setReproLoading(false);
      }
    }
  }, [canReadRepro, workspace.selectedHouse]);

  const currentBatches = useMemo(
    () => inProgressProductionBatches(batches),
    [batches],
  );
  const currentBatch = useMemo(
    () =>
      currentBatches.find((batch) => String(batch.id) === selectedBatchId) ??
      null,
    [currentBatches, selectedBatchId],
  );

  useEffect(() => {
    setSelectedBatchId((current) =>
      currentBatches.some((batch) => String(batch.id) === current)
        ? current
        : String(currentBatches[0]?.id ?? ""),
    );
  }, [currentBatches]);

  const loadBatchStatistics = useCallback(async () => {
    const loadVersion = ++batchStatisticsLoadVersion.current;
    const houseId = workspace.selectedHouse?.id;
    if (
      !houseId ||
      !canReadRepro ||
      !currentBatch ||
      currentBatch.houseId !== houseId
    ) {
      setBatchStatistics(null);
      setBatchStatisticsStatus("idle");
      return;
    }
    setBatchStatistics(null);
    setBatchStatisticsStatus("loading");
    try {
      const nextStatistics = await getBatchStatistics(houseId, currentBatch.id);
      if (loadVersion !== batchStatisticsLoadVersion.current) return;
      setBatchStatistics(nextStatistics);
      setBatchStatisticsStatus("ready");
    } catch {
      if (loadVersion !== batchStatisticsLoadVersion.current) return;
      setBatchStatistics(null);
      setBatchStatisticsStatus("error");
    }
  }, [canReadRepro, currentBatch, workspace.selectedHouse]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadReproDictionaries();
  }, [loadReproDictionaries]);

  useEffect(() => {
    void loadBatchStatistics();
  }, [loadBatchStatistics]);

  const filteredCages = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return cages.filter((cage) => {
      const matchesQuery =
        !normalized ||
        [cage.cageNumber, cage.rowCode, cage.remark, cage.id.toString()].some(
          (value) => value?.toLowerCase().includes(normalized),
        );
      if (!matchesQuery) return false;
      if (breedingCageFilter === "doe") {
        return cage.status === "1" && cage.breedingOccupantGender === "0";
      }
      if (breedingCageFilter === "buck") {
        return cage.status === "1" && cage.breedingOccupantGender === "1";
      }
      return true;
    });
  }, [breedingCageFilter, cages, query]);

  // 地图用全量笼位构建，筛选只决定哪些格子变淡：
  // 把未命中的笼从图上拿掉会让坐标错位，“第几排第几位”就不可信了。
  const cageLayout = useMemo(() => buildCageLayout(cages), [cages]);
  const matchedCageIds = useMemo(
    () => new Set(filteredCages.map((cage) => cage.id)),
    [filteredCages],
  );

  const filteredRabbits = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return rabbits;
    }
    return rabbits.filter((rabbit) =>
      [rabbit.id.toString(), rabbit.breed, rabbitTypeLabel(rabbit)].some(
        (value) => value?.toLowerCase().includes(normalized),
      ),
    );
  }, [query, rabbits]);

  return (
    <>
      <PageHeader
        title="兔群管理"
        description={workspace.selectedHouse?.name ?? "请选择兔场"}
        actions={
          <>
            <HousePermissionBadge permission={workspace.permission} />
            <Button
              variant="outline"
              onClick={() =>
                void Promise.all([load(), loadReproDictionaries()])
              }
              disabled={loading || !workspace.selectedHouse}
            >
              <RefreshCwIcon data-icon="inline-start" />
              刷新
            </Button>
          </>
        }
      />

      {workspace.selectedHouse && canReadRepro ? (
        <section
          className="motion-section border-y py-4"
          aria-labelledby="current-batch-statistics-title"
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2
                id="current-batch-statistics-title"
                className="text-base font-semibold"
              >
                当前生产批次
              </h2>
              <p className="mt-1 text-sm text-muted-foreground">
                产崽和断奶统计随所选批次更新。
              </p>
            </div>
            {!reproLoading && currentBatches.length > 0 ? (
              <Select
                value={selectedBatchId}
                onValueChange={setSelectedBatchId}
              >
                <SelectTrigger
                  className="w-full sm:w-52"
                  aria-label="选择当前生产批次"
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {currentBatches.map((batch) => (
                      <SelectItem key={batch.id} value={String(batch.id)}>
                        {batch.batchCode}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            ) : null}
          </div>
          <div className="mt-4">
            {reproLoading ? (
              <BatchStatisticsSummary statistics={null} status="loading" />
            ) : reproLoadFailed ? (
              <p className="text-sm text-muted-foreground" role="alert">
                当前批次读取失败，请刷新后重试。
              </p>
            ) : currentBatches.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                当前没有进行中的批次。建立批次后会显示统计。
              </p>
            ) : (
              <BatchStatisticsSummary
                statistics={batchStatistics}
                status={batchStatisticsStatus}
                onRetry={() => void loadBatchStatistics()}
              />
            )}
          </div>
        </section>
      ) : null}

      {!workspace.selectedHouse ? (
        <Empty>
          <WarehouseIcon aria-hidden="true" />
          <EmptyTitle>请选择兔场</EmptyTitle>
          <EmptyDescription>兔群数据始终限定在一个兔场内。</EmptyDescription>
        </Empty>
      ) : (
        <Tabs defaultValue="rabbits">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <TabsList>
              <TabsTrigger value="rabbits">兔只 {rabbits.length}</TabsTrigger>
              <TabsTrigger value="cages">笼位 {cages.length}</TabsTrigger>
            </TabsList>
            <form
              className="flex w-full gap-2 sm:max-w-sm"
              onSubmit={(event) => {
                event.preventDefault();
                setQuery(queryInput);
              }}
            >
              <Input
                value={queryInput}
                placeholder="ID、品种或笼位编号"
                aria-label="搜索兔群"
                onChange={(event) => setQueryInput(event.target.value)}
              />
              <Button
                type="submit"
                variant="outline"
                size="icon"
                aria-label="查询"
              >
                <SearchIcon aria-hidden="true" />
              </Button>
            </form>
          </div>

          <TabsContent value="rabbits">
            <Card>
              <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
                <div className="flex flex-col gap-1.5">
                  <CardTitle>兔只</CardTitle>
                  <CardDescription>
                    维护入场信息、笼位、品种和体重。
                  </CardDescription>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    onClick={() => setRangeEntryOpen(true)}
                    disabled={!canEdit || cages.length === 0}
                  >
                    <Grid2X2Icon data-icon="inline-start" />
                    范围入栏
                  </Button>
                  <Button
                    onClick={() => {
                      setRabbitInitialCageId(null);
                      setRabbitCreateOpen(true);
                    }}
                    disabled={!canEdit || cages.length === 0}
                  >
                    <PlusIcon data-icon="inline-start" />
                    录入兔只
                  </Button>
                </div>
              </CardHeader>
              <CardContent>
                {/* 表格带最小宽度：窄屏下宁可横向滚动（DESIGN.md 允许），也不能把列挤成
                    一列一个字——那时“商品兔笼 1-3-1”会折成三行、“商品兔”竖着排，行身份就读不出来。 */}
                {filteredRabbits.length === 0 ? (
                  <Empty>
                    <RabbitIcon aria-hidden="true" />
                    <EmptyTitle>没有匹配的兔只</EmptyTitle>
                    <EmptyDescription>
                      清除查询条件或录入第一只兔。
                    </EmptyDescription>
                  </Empty>
                ) : (
                  <Table className="min-w-[860px]">
                    <TableHeader>
                      <TableRow>
                        <TableHead>兔只</TableHead>
                        <TableHead>笼位</TableHead>
                        <TableHead>品种</TableHead>
                        <TableHead>体重</TableHead>
                        <TableHead>状态</TableHead>
                        <TableHead className="text-right">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredRabbits.map((rabbit) => (
                        <TableRow key={rabbit.id}>
                          <TableCell>
                            <div className="flex min-w-32 flex-col gap-1">
                              <span className="font-medium">
                                兔 #{rabbit.id}
                              </span>
                              <span className="text-xs text-muted-foreground">
                                {rabbitTypeLabel(rabbit)} ·{" "}
                                {rabbit.gender === "0"
                                  ? "母"
                                  : rabbit.gender === "1"
                                    ? "公"
                                    : "未知"}
                              </span>
                            </div>
                          </TableCell>
                          <TableCell>
                            {cages.find((cage) => cage.id === rabbit.cageId)
                              ?.cageNumber ?? `#${rabbit.cageId}`}
                          </TableCell>
                          <TableCell>{rabbit.breed || "-"}</TableCell>
                          <TableCell>
                            {rabbit.weight
                              ? `${rabbit.weight.toFixed(2)} kg`
                              : "-"}
                          </TableCell>
                          <TableCell>
                            <div className="flex min-w-28 flex-col items-start gap-1">
                              <Badge
                                variant={
                                  rabbit.isActive ? "default" : "secondary"
                                }
                              >
                                {rabbit.isActive ? "在栏" : "离场"}
                              </Badge>
                              <span className="text-xs text-muted-foreground">
                                {rabbitStageSummary(rabbit, reproStageLabels)}
                              </span>
                            </div>
                          </TableCell>
                          <TableCell>
                            <div className="flex justify-end">
                              <Button variant="outline" size="sm" asChild>
                                <Link
                                  to={`/workspace/livestock/rabbits/${rabbit.id}`}
                                >
                                  <EyeIcon data-icon="inline-start" />
                                  查看详情
                                </Link>
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="cages">
            <Card>
              <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
                <div className="flex flex-col gap-1.5">
                  <CardTitle>笼位</CardTitle>
                  <CardDescription>
                    新增笼位和启停笼位需要控制权限。
                  </CardDescription>
                  {canReadRepro ? (
                    <p
                      className="text-sm text-muted-foreground"
                      data-testid="pending-commodity-allocation-count"
                    >
                      {pendingCommodityAllocationCount === null
                        ? "待分配入笼商品兔读取失败，可刷新重试"
                        : `待分配入笼商品兔 ${pendingCommodityAllocationCount} 只`}
                    </p>
                  ) : null}
                </div>
                <Button
                  onClick={() => setCageDialog({ open: true, cage: null })}
                  disabled={!canControl}
                >
                  <PlusIcon data-icon="inline-start" />
                  新增笼位
                </Button>
              </CardHeader>
              <CardContent>
                <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <p className="text-sm text-muted-foreground">
                    种兔笼按实际在栏种兔性别筛选。
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
                    <div
                      className="flex items-center gap-1"
                      role="group"
                      aria-label="笼位显示方式"
                    >
                      <Button
                        variant={cageView === "map" ? "secondary" : "outline"}
                        size="sm"
                        aria-pressed={cageView === "map"}
                        onClick={() => setCageView("map")}
                      >
                        <Grid2X2Icon data-icon="inline-start" />
                        分层地图
                      </Button>
                      <Button
                        variant={cageView === "list" ? "secondary" : "outline"}
                        size="sm"
                        aria-pressed={cageView === "list"}
                        onClick={() => setCageView("list")}
                      >
                        <ListIcon data-icon="inline-start" />
                        列表
                      </Button>
                    </div>
                    <Select
                      value={breedingCageFilter}
                      onValueChange={(value) =>
                        setBreedingCageFilter(value as BreedingCageFilter)
                      }
                    >
                      <SelectTrigger
                        className="w-full sm:w-44"
                        aria-label="筛选种兔笼"
                      >
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value="all">全部笼位</SelectItem>
                          <SelectItem value="doe">种母兔笼</SelectItem>
                          <SelectItem value="buck">种公兔笼</SelectItem>
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                {cages.length > 0 && cageView === "map" ? (
                  <div className="flex flex-col gap-3">
                    <CageAttentionLegend cages={cages} />
                    <CageMap
                      layout={cageLayout}
                      isMatch={(cage) => matchedCageIds.has(cage.id)}
                      visibleRowLimit={visibleRowCount}
                      onShowMoreRows={() =>
                        setVisibleRowCount(
                          (current) => current + CAGE_ROW_BATCH,
                        )
                      }
                      onSelectCage={setCageDetail}
                    />
                    <p className="text-xs text-muted-foreground">
                      点格子查看笼内兔只；编辑与删除笼位在列表视图里。
                    </p>
                  </div>
                ) : filteredCages.length === 0 ? (
                  <Empty>
                    <EmptyTitle>没有匹配的笼位</EmptyTitle>
                    <EmptyDescription>
                      清除查询条件或新增笼位。
                    </EmptyDescription>
                  </Empty>
                ) : (
                  <Table className="min-w-[820px]">
                    <TableHeader>
                      <TableRow>
                        <TableHead>笼位</TableHead>
                        <TableHead>位置</TableHead>
                        <TableHead>用途</TableHead>
                        <TableHead>兔只</TableHead>
                        <TableHead>状态</TableHead>
                        <TableHead className="text-right">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredCages.map((cage) => (
                        <TableRow key={cage.id}>
                          <TableCell>
                            <div className="flex min-w-32 flex-col gap-1">
                              <span className="font-medium">
                                {cage.cageNumber}
                              </span>
                              <span className="text-xs text-muted-foreground">
                                ID {cage.id}
                              </span>
                            </div>
                          </TableCell>
                          <TableCell>
                            {cage.rowCode || "-"} / {cage.positionIndex ?? "-"}{" "}
                            / {cage.layerIndex ?? "-"}
                          </TableCell>
                          <TableCell>{cageUsageLabel(cage)}</TableCell>
                          <TableCell>{cage.rabbitCount} 只</TableCell>
                          <TableCell>
                            <Badge
                              variant={cage.isEnabled ? "default" : "secondary"}
                            >
                              {cage.isEnabled ? "启用" : "停用"}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <div className="flex flex-wrap justify-end gap-2">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setCageDetail(cage)}
                              >
                                <RabbitIcon data-icon="inline-start" />
                                笼内兔只
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                disabled={!canControl}
                                onClick={() =>
                                  setCageDialog({ open: true, cage })
                                }
                              >
                                <Edit3Icon data-icon="inline-start" />
                                编辑
                              </Button>
                              <Button
                                variant="destructive"
                                size="sm"
                                disabled={!canControl || cage.rabbitCount > 0}
                                onClick={() => setDeleteTarget(cage)}
                              >
                                <Trash2Icon data-icon="inline-start" />
                                删除
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      )}

      <CageDialog
        state={cageDialog}
        onOpenChange={(open) =>
          setCageDialog((current) => ({ ...current, open }))
        }
        houseId={workspace.selectedHouse?.id ?? null}
        onSaved={load}
      />
      <RabbitFormDialog
        open={rabbitCreateOpen}
        rabbit={null}
        onOpenChange={(open) => {
          setRabbitCreateOpen(open);
          if (!open) setRabbitInitialCageId(null);
        }}
        houseId={workspace.selectedHouse?.id ?? null}
        cages={cages}
        entryPoints={entryPoints}
        batches={batches}
        initialCageId={rabbitInitialCageId}
        onSaved={load}
      />
      <RabbitRangeEntryDialog
        open={rangeEntryOpen}
        houseId={workspace.selectedHouse?.id ?? null}
        cages={cages}
        onOpenChange={setRangeEntryOpen}
        onSaved={load}
      />
      <DeleteCageDialog
        cage={deleteTarget}
        houseId={workspace.selectedHouse?.id ?? null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        onDeleted={load}
      />
      <CageRabbitsDialog
        cage={cageDetail}
        rabbits={rabbits}
        stageLabels={reproStageLabels}
        canCreateRabbit={canEdit}
        onCreateRabbit={(cageId) => {
          setCageDetail(null);
          setRabbitInitialCageId(cageId);
          setRabbitCreateOpen(true);
        }}
        onOpenChange={(open) => !open && setCageDetail(null)}
      />
    </>
  );
}

function cageUsageLabel(cage: Cage) {
  if (cage.status === "1" && cage.breedingOccupantGender === "0")
    return "种母兔笼";
  if (cage.status === "1" && cage.breedingOccupantGender === "1")
    return "种公兔笼";
  return cageStatusLabels[cage.status ?? ""] ?? cage.status ?? "-";
}

function CageDialog({
  state,
  onOpenChange,
  houseId,
  onSaved,
}: {
  state: { open: boolean; cage: Cage | null };
  onOpenChange: (open: boolean) => void;
  houseId: number | null;
  onSaved: () => Promise<void>;
}) {
  const [number, setNumber] = useState("");
  const [rowCode, setRowCode] = useState("");
  const [position, setPosition] = useState("");
  const [layer, setLayer] = useState("");
  const [remark, setRemark] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!state.open) return;
    setNumber(state.cage?.cageNumber ?? "");
    setRowCode(state.cage?.rowCode ?? "");
    setPosition(state.cage?.positionIndex?.toString() ?? "");
    setLayer(state.cage?.layerIndex?.toString() ?? "");
    setRemark(state.cage?.remark ?? "");
    setEnabled(state.cage?.isEnabled ?? true);
  }, [state.cage, state.open]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!houseId) return;
    setSaving(true);
    const data = {
      // 留空就不传，由后端按「排-位-层」生成，跟建兔舍自动铺的笼位一致。
      cageNumber: number.trim() || undefined,
      rowCode: rowCode.trim() || undefined,
      positionIndex: position ? Number(position) : undefined,
      layerIndex: layer ? Number(layer) : undefined,
      remark: remark.trim(),
      isEnabled: enabled,
    };
    try {
      if (state.cage) {
        // 编辑时留空意味着「不改编号」，不是「把编号抹掉」。
        await updateCage(houseId, state.cage.id, {
          ...data,
          cageNumber: data.cageNumber ?? state.cage.cageNumber,
        });
        toast.success("笼位已更新");
      } else {
        await createCage(houseId, data);
        toast.success("笼位已新增");
      }
      onOpenChange(false);
      await onSaved();
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={state.open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{state.cage ? "编辑笼位" : "新增笼位"}</DialogTitle>
          <DialogDescription>
            维护笼位编号、物理位置和启用状态。
          </DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto pr-1">
            <Field>
              <FieldLabel htmlFor="cage-number">笼位编号</FieldLabel>
              <Input
                id="cage-number"
                value={number}
                maxLength={50}
                placeholder="留空按「排-位-层」自动生成，如 2-3-1"
                onChange={(event) => setNumber(event.target.value)}
              />
              <FieldDescription>
                填全下面的排号、位号、层号就不用自己编号；只有角落里那种没有规整坐标的笼位才需要手填。
              </FieldDescription>
            </Field>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field>
                <FieldLabel htmlFor="cage-row">排号</FieldLabel>
                <Input
                  id="cage-row"
                  value={rowCode}
                  maxLength={40}
                  onChange={(event) => setRowCode(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="cage-position">列位</FieldLabel>
                <Input
                  id="cage-position"
                  type="number"
                  min={1}
                  value={position}
                  onChange={(event) => setPosition(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="cage-layer">层位</FieldLabel>
                <Input
                  id="cage-layer"
                  type="number"
                  min={1}
                  value={layer}
                  onChange={(event) => setLayer(event.target.value)}
                />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="cage-remark">备注</FieldLabel>
              <Textarea
                id="cage-remark"
                value={remark}
                onChange={(event) => setRemark(event.target.value)}
              />
            </Field>
            <Field>
              <label
                className="flex items-center gap-3 text-sm font-medium"
                htmlFor="cage-enabled"
              >
                <input
                  id="cage-enabled"
                  type="checkbox"
                  checked={enabled}
                  onChange={(event) => setEnabled(event.target.checked)}
                />
                启用笼位
              </label>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner data-icon="inline-start" /> : null}保存
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/**
 * 笼内兔只清单（飞书 recvsrEA6TRuK6）。
 *
 * 现场人员是按笼子找兔的，没有这个入口就只能回到兔只列表里逐条对笼位号。
 */
function CageRabbitsDialog({
  cage,
  rabbits,
  stageLabels,
  canCreateRabbit,
  onCreateRabbit,
  onOpenChange,
}: {
  cage: Cage | null;
  rabbits: Rabbit[];
  stageLabels: Record<string, string>;
  canCreateRabbit: boolean;
  onCreateRabbit: (cageId: number) => void;
  onOpenChange: (open: boolean) => void;
}) {
  const members = useMemo(
    () =>
      rabbits.filter((rabbit) => rabbit.cageId === cage?.id && rabbit.isActive),
    [cage?.id, rabbits],
  );

  return (
    <Dialog open={Boolean(cage)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{cage?.cageNumber ?? ""} 笼内兔只</DialogTitle>
          <DialogDescription>在栏 {members.length} 只。</DialogDescription>
        </DialogHeader>
        {members.length === 0 ? (
          <Empty>
            <RabbitIcon aria-hidden="true" />
            <EmptyTitle>笼内没有在栏兔</EmptyTitle>
            <EmptyDescription>
              录入兔只或把其它笼位的兔换过来。
            </EmptyDescription>
          </Empty>
        ) : (
          <div className="flex max-h-96 flex-col gap-3 overflow-y-auto pr-1">
            {members.map((rabbit) => (
              <div
                key={rabbit.id}
                className="flex flex-col gap-2 rounded-md border p-3"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">兔 #{rabbit.id}</span>
                  <Badge variant="secondary">{rabbitTypeLabel(rabbit)}</Badge>
                </div>
                <span className="text-xs text-muted-foreground">
                  {rabbit.breed || "未填品种"} ·{" "}
                  {rabbitStageSummary(rabbit, stageLabels)}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  className="self-end"
                  asChild
                >
                  <Link to={`/workspace/livestock/rabbits/${rabbit.id}`}>
                    <EyeIcon data-icon="inline-start" />
                    查看详情
                  </Link>
                </Button>
              </div>
            ))}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            关闭
          </Button>
          <Button
            disabled={
              !cage || !canCreateRabbit || !cageAcceptsMoreRabbits(cage)
            }
            onClick={() => cage && onCreateRabbit(cage.id)}
          >
            <PlusIcon data-icon="inline-start" />
            录入兔只
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function DeleteCageDialog({
  cage,
  houseId,
  onOpenChange,
  onDeleted,
}: {
  cage: Cage | null;
  houseId: number | null;
  onOpenChange: (open: boolean) => void;
  onDeleted: () => Promise<void>;
}) {
  const [saving, setSaving] = useState(false);

  async function handleDelete() {
    if (!cage || !houseId) return;
    setSaving(true);
    try {
      await deleteCage(houseId, cage.id);
      toast.success("笼位已删除");
      onOpenChange(false);
      await onDeleted();
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={Boolean(cage)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认删除笼位</DialogTitle>
          <DialogDescription>
            仅空笼位可以删除。将删除“{cage?.cageNumber ?? ""}”。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            variant="destructive"
            disabled={saving}
            onClick={() => void handleDelete()}
          >
            {saving ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <Trash2Icon data-icon="inline-start" />
            )}
            删除笼位
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
