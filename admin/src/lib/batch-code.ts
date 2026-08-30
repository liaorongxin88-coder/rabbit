export const BATCH_CODE_MAX_LENGTH = 100;

const FARM_UTC_OFFSET_MS = 8 * 60 * 60 * 1000;
const BATCH_TIMESTAMP_LENGTH = 13;

/** 新建批次时预填的编号，格式 `东一舍-20260220-1530`。 */
export function defaultBatchCode(houseName: string, date = new Date()) {
 const farmDate = new Date(date.getTime() + FARM_UTC_OFFSET_MS);
 const timestamp = [
  farmDate.getUTCFullYear(),
  String(farmDate.getUTCMonth() + 1).padStart(2, "0"),
  String(farmDate.getUTCDate()).padStart(2, "0"),
  "-",
  String(farmDate.getUTCHours()).padStart(2, "0"),
  String(farmDate.getUTCMinutes()).padStart(2, "0"),
 ].join("");
 const normalized = normalizeHouseName(houseName);
 const maxHouseLength = BATCH_CODE_MAX_LENGTH - BATCH_TIMESTAMP_LENGTH - 1;
 const safeHouseName = Array.from(normalized).slice(0, maxHouseLength).join("");
 return `${safeHouseName}-${timestamp}`;
}

export function batchCodeDraftForDialog(
 currentCode: string,
 isOpening: boolean,
 houseName: string,
 date = new Date(),
) {
 return isOpening ? defaultBatchCode(houseName, date) : currentCode;
}

function normalizeHouseName(value: string) {
 const normalized = value
  .trim()
  .replace(/[\s\-_/\u2013\u2014]+/g, "-")
  .replace(/^-+|-+$/g, "");
 return normalized || "兔舍";
}
