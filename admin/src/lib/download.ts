const SAFE_FILENAME = /[^\p{L}\p{N}._()\- ]/gu;

export const XLSX_MEDIA_TYPE =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

export function sanitizeDownloadFilename(filename: string, fallback: string) {
  const basename = filename.split(/[\\/]/).pop()?.trim() ?? "";
  const printable = [...basename]
    .filter((character) => {
      const code = character.charCodeAt(0);
      return code > 31 && code !== 127;
    })
    .join("");
  const sanitized = printable.replace(SAFE_FILENAME, "_").replace(/[. ]+$/g, "");
  if (!sanitized || sanitized === "." || sanitized === "..") return fallback;
  const stem = sanitized.split(".")[0].toUpperCase();
  return /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$/.test(stem)
    ? `_${sanitized}`
    : sanitized;
}

export function parseContentDispositionFilename(
  contentDisposition: string | null,
  fallback: string,
) {
  if (!contentDisposition) return fallback;

  const encodedMatch = contentDisposition.match(
    /filename\*\s*=\s*(?:"([^"]*)"|([^;]+))/i,
  );
  const encoded = (encodedMatch?.[1] ?? encodedMatch?.[2] ?? "").trim();
  if (encoded) {
    try {
      const extended = encoded.match(/^([^']*)'[^']*'(.*)$/);
      const charset = extended?.[1]?.toLowerCase();
      if (charset && charset !== "utf-8" && charset !== "utf8") {
        throw new Error("Unsupported Content-Disposition charset");
      }
      return sanitizeDownloadFilename(
        decodeURIComponent((extended?.[2] ?? encoded).replace(/^"|"$/g, "")),
        fallback,
      );
    } catch {
      // Fall through to the ASCII filename.
    }
  }

  const ascii = contentDisposition.match(
    /filename\s*=\s*(?:"([^"]+)"|([^;]+))/i,
  );
  return sanitizeDownloadFilename(
    (ascii?.[1] ?? ascii?.[2] ?? "").trim(),
    fallback,
  );
}

export function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  try {
    anchor.href = url;
    anchor.download = filename;
    document.body.append(anchor);
    anchor.click();
  } finally {
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
  }
}
