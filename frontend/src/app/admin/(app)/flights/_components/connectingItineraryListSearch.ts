export const applyItineraryListSearch = (keyword: string) => ({
  keyword: keyword.trim(),
  page: 1,
});
export const clearItineraryListSearch = () => ({ keyword: "", page: 1 });
