(function () {
  const form = document.getElementById("registerForm");
  if (!form) return;

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    setMsg("msg", "");
    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value;
    const r = await postForm("/api/register", { username, password });
    if (r && r.success) {
      setMsg("msg", "注册成功，请返回登录。");
      setTimeout(() => window.location.href = "/login", 800);
      return;
    }
    setMsg("msg", (r && r.message) ? r.message : "注册失败");
  });
})();

