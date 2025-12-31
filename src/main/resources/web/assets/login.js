(function () {
  const form = document.getElementById("loginForm");
  if (!form) return;

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    setMsg("msg", "");
    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value;
    const r = await postForm("/api/login", { username, password });
    if (r && r.success) {
      window.location.href = r.redirect || "/app";
      return;
    }
    setMsg("msg", (r && r.message) ? r.message : "登录失败");
  });
})();

