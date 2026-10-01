// Synthetic category pages only. Zero retains the older one-category fixture.
export function boundedCategoryCount(value) {
  const count = Number(value);
  return Number.isFinite(count) ? Math.max(0, Math.min(1000, Math.trunc(count))) : 0;
}
export function categoryFixturePage(url, count) {
  const limit = Number(url.searchParams.get('limit') ?? 200);
  const bad = () => ({ status: 400, body: { error: 'Invalid synthetic category cursor.', error_code: 'invalid_catalog_cursor' } });
  if (!Number.isSafeInteger(limit) || limit < 1 || limit > 200) return bad();
  let offset = 0;
  const token = url.searchParams.get('cursor');
  if (token) {
    try {
      if (token.length > 512) return bad();
      const page = JSON.parse(Buffer.from(token, 'base64url').toString());
      if (page.kind !== 'categories' || page.count !== count || page.limit !== limit ||
          !Number.isSafeInteger(page.offset) || page.offset < 0 || page.offset >= count) return bad();
      offset = page.offset;
    } catch { return bad(); }
  }
  const cursor = index => Buffer.from(JSON.stringify({ kind: 'categories', count, limit, offset: index })).toString('base64url');
  const items = count ? Array.from({ length: Math.min(limit, count - offset) }, (_, index) => ({ id: `category-${offset + index}`, name: `Category ${String(offset + index + 1).padStart(3, '0')}` })) : [{ id: 'news', name: 'News' }];
  return { status: 200, body: { catalog_id: 1, generation: 1, items,
    next_cursor: count && offset + limit < count ? cursor(offset + limit) : null,
    previous_cursor: offset > 0 ? cursor(Math.max(0, offset - limit)) : null } };
}
