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

