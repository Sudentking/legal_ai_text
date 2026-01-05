(async function () {
  const me = await getJson("/api/me");
  if (!me || !me.success) {
    window.location.href = "/login";
    return;
  }
  const u = me.user || {};
  if (u.role !== "SUPER_ADMIN") {
    document.body.innerHTML = "<main class='container'><div class='card'>403 FORBIDDEN</div></main>";
    return;
  }
  const who = document.getElementById("who");
  if (who) who.textContent = `${u.username || "admin"} (${u.role})`;

  const logoutBtn = document.getElementById("logoutBtn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", async () => {
      await postForm("/api/logout", {});
      window.location.href = "/login";
    });
  }

  const logsEl = document.getElementById("logs");
  function getLimit() {
    const v = document.getElementById("logLimit").value;
    const n = parseInt(v || "50", 10);
    return Number.isFinite(n) ? n : 50;
  }
  function getSessionId() {
    return (document.getElementById("logSessionId").value || "").trim();
  }

  document.getElementById("loadQaLogs").addEventListener("click", async () => {
    const qs = new URLSearchParams({ limit: String(getLimit()) });
    const sid = getSessionId();
    if (sid) qs.set("sessionId", sid);
    const r = await getJson("/api/admin/qa-logs?" + qs.toString());
    logsEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("loadTaskHistory").addEventListener("click", async () => {
    const qs = new URLSearchParams({ limit: String(getLimit()) });
    const sid = getSessionId();
    if (sid) qs.set("sessionId", sid);
    const r = await getJson("/api/admin/task-history?" + qs.toString());
    logsEl.textContent = JSON.stringify(r, null, 2);
  });

  const kbEl = document.getElementById("kb");
  function kbLimit() {
    const v = document.getElementById("kbLimit").value;
    const n = parseInt(v || "100", 10);
    return Number.isFinite(n) ? n : 100;
  }
  function kbLawCode() {
    return (document.getElementById("kbLawCode").value || "").trim();
  }
  function kbArticleLimit() {
    const v = document.getElementById("kbArticleLimit").value;
    const n = parseInt(v || "50", 10);
    return Number.isFinite(n) ? n : 50;
  }
  function kbOffset() {
    const v = document.getElementById("kbOffset").value;
    const n = parseInt(v || "0", 10);
    return Number.isFinite(n) ? n : 0;
  }
  function kbArticleId() {
    return (document.getElementById("kbArticleId").value || "").trim();
  }
  function kbChunkSize() {
    const v = document.getElementById("kbChunkSize").value;
    const n = parseInt(v || "400", 10);
    return Number.isFinite(n) ? n : 400;
  }

  document.getElementById("loadKbLaws").addEventListener("click", async () => {
    const qs = new URLSearchParams({ limit: String(kbLimit()) });
    const r = await getJson("/api/admin/kb/laws?" + qs.toString());
    kbEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("loadKbArticles").addEventListener("click", async () => {
    const lawCode = kbLawCode();
    if (!lawCode) {
      kbEl.textContent = "请先填写 law_code 过滤（例如：民法典）。";
      return;
    }
    const qs = new URLSearchParams({
      lawCode,
      limit: String(kbArticleLimit()),
      offset: String(kbOffset())
    });
    const r = await getJson("/api/admin/kb/articles?" + qs.toString());
    kbEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("loadKbArticle").addEventListener("click", async () => {
    const id = kbArticleId();
    if (!id) {
      kbEl.textContent = "请填写条文 id（law_text.id）。";
      return;
    }
    const qs = new URLSearchParams({ id });
    const r = await getJson("/api/admin/kb/article?" + qs.toString());
    kbEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("deleteKbArticle").addEventListener("click", async () => {
    const id = kbArticleId();
    if (!id) {
      kbEl.textContent = "请填写条文 id（law_text.id）。";
      return;
    }
    const ok = prompt(`危险操作：将删除条文 id=${id} 及其切片/向量。\n请输入 DELETE 确认：`);
    if (ok !== "DELETE") {
      kbEl.textContent = "已取消。";
      return;
    }
    const r = await postForm("/api/admin/kb/delete/article", { id });
    kbEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("rebuildKbArticle").addEventListener("click", async () => {
    const id = kbArticleId();
    if (!id) {
      kbEl.textContent = "请填写条文 id（law_text.id）。";
      return;
    }
    kbEl.textContent = "重建中...";
    const r = await postForm("/api/admin/kb/rebuild/article", { id, chunkSize: String(kbChunkSize()) });
    kbEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("deleteKbLaw").addEventListener("click", async () => {
    const lawCode = kbLawCode();
    if (!lawCode) {
      kbEl.textContent = "请先填写 law_code（用于删除整部法律）。";
      return;
    }
    const ok = prompt(`危险操作：将删除整部法律 law_code=${lawCode} 的所有条文及其切片/向量。\n请输入 DELETE 确认：`);
    if (ok !== "DELETE") {
      kbEl.textContent = "已取消。";
      return;
    }
    const r = await postForm("/api/admin/kb/delete/law", { lawCode });
    kbEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("rebuildKbLaw").addEventListener("click", async () => {
    const lawCode = kbLawCode();
    if (!lawCode) {
      kbEl.textContent = "请先填写 law_code（用于重建整部法律）。";
      return;
    }
    kbEl.textContent = "重建中（可能耗时较长）...";
    const r = await postForm("/api/admin/kb/rebuild/law", { lawCode, chunkSize: String(kbChunkSize()) });
    kbEl.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("checkKbQuality").addEventListener("click", async () => {
    const lawCode = kbLawCode();
    if (!lawCode) {
      kbEl.textContent = "请先填写 law_code（用于质量检查）。";
      return;
    }
    const qs = new URLSearchParams({ lawCode });
    const r = await getJson("/api/admin/kb/quality?" + qs.toString());
    kbEl.textContent = JSON.stringify(r, null, 2);
  });

  const importForm = document.getElementById("importForm");
  const importResult = document.getElementById("importResult");
  importForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    importResult.textContent = "导入中...";
    const paths = document.getElementById("paths").value;
    const recursive = document.getElementById("recursive").checked ? "true" : "false";
    const chunkSize = document.getElementById("chunkSize").value;
    const noSkip = document.getElementById("noSkip").checked ? "true" : "false";
    const r = await postForm("/api/admin/import/batch", { paths, recursive, chunkSize, noSkip });
    importResult.textContent = JSON.stringify(r, null, 2);
  });

  const permResult = document.getElementById("permResult");
  function permPayload() {
    return {
      username: (document.getElementById("permUser").value || "").trim(),
      permission: (document.getElementById("permCode").value || "").trim()
    };
  }

  document.getElementById("grantPerm").addEventListener("click", async () => {
    permResult.textContent = "处理中...";
    const r = await postForm("/api/admin/permission/grant", permPayload());
    permResult.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("revokePerm").addEventListener("click", async () => {
    permResult.textContent = "处理中...";
    const r = await postForm("/api/admin/permission/revoke", permPayload());
    permResult.textContent = JSON.stringify(r, null, 2);
  });
  document.getElementById("listPerm").addEventListener("click", async () => {
    permResult.textContent = "处理中...";
    const u = (document.getElementById("permUser").value || "").trim();
    const qs = new URLSearchParams({ username: u });
    const r = await getJson("/api/admin/permission/list?" + qs.toString());
    permResult.textContent = JSON.stringify(r, null, 2);
  });
})();
