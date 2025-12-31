async function postForm(url, data) {
  const body = new URLSearchParams();
  for (const [k, v] of Object.entries(data || {})) {
    body.set(k, v == null ? "" : String(v));
  }
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
    credentials: "include"
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch (e) { json = { success: false, message: text }; }
  json._status = res.status;
  return json;
}

async function getJson(url) {
  const res = await fetch(url, { method: "GET", credentials: "include" });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch (e) { json = { success: false, message: text }; }
  json._status = res.status;
  return json;
}

function setMsg(id, text) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = text || "";
}

