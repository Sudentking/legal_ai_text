(async function () {
  const me = await getJson("/api/me");
  if (!me || !me.success) {
    window.location.href = "/login";
    return;
  }
  const who = document.getElementById("who");
  if (who) {
    const u = me.user || {};
    who.textContent = `${u.username || "user"} (${u.role || ""})`;
  }

  const logoutBtn = document.getElementById("logoutBtn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", async () => {
      await postForm("/api/logout", {});
      window.location.href = "/login";
    });
  }

  const chat = document.getElementById("chat");
  function appendBubble(cls, text) {
    const div = document.createElement("div");
    div.className = `bubble ${cls}`;
    div.textContent = text;
    chat.appendChild(div);
    chat.scrollTop = chat.scrollHeight;
  }

  const form = document.getElementById("askForm");
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    setMsg("msg", "");
    const qEl = document.getElementById("question");
    const question = (qEl.value || "").trim();
    if (!question) return;
    appendBubble("user", question);
    qEl.value = "";
    const r = await postForm("/api/ask", { question });
    if (r && r.success) {
      appendBubble("bot", r.answer || "");
    } else {
      appendBubble("bot", (r && r.message) ? `错误：${r.message}` : "错误：请求失败");
    }
  });
})();

