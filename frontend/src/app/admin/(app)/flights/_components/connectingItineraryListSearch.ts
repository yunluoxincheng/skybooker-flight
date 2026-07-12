export const SEGMENT_SCOPE_LABELS = {
  ALL: "全部航段",
  FIRST: "第一航段",
  SECOND: "第二航段",
} as const;

export type SegmentScope = keyof typeof SEGMENT_SCOPE_LABELS;

export const isSegmentScope = (value: string): value is SegmentScope =>
  value in SEGMENT_SCOPE_LABELS;

export const applyItineraryListSearch = (keyword: string) => ({
  keyword: keyword.trim(),
  page: 1,
});
export const clearItineraryListSearch = () => ({ keyword: "", page: 1 });
