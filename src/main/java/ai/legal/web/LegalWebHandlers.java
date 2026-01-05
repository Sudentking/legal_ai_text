package ai.legal.web;

import ai.legal.importer.LawDocumentBatchImportRequest;
import ai.legal.importer.LawDocumentImportBatchResult;
import ai.legal.importer.LawDocumentImportFileResult;
import ai.legal.model.AgentTaskHistory;
import ai.legal.model.LawCodeSummary;
import ai.legal.model.LawText;
import ai.legal.model.PermissionCode;
import ai.legal.model.QaLog;
import ai.legal.model.UserAccount;
import ai.legal.model.UserRole;
import ai.legal.service.auth.LoginResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Handler 注册与实现。
 */
public final class LegalWebHandlers {

    private static final String COOKIE_SESSION_ID = "SESSION_ID";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private LegalWebHandlers() {
    }

    public static void register(HttpServer server, WebAppContext ctx) {
        server.createContext("/", new RootHandler(ctx));
        server.createContext("/login", new LoginPageHandler(ctx));
        server.createContext("/register", new RegisterPageHandler(ctx));
        server.createContext("/app", new AppPageHandler(ctx));
        server.createContext("/admin", new AdminPageHandler(ctx));
        server.createContext("/assets/", new StaticAssetsHandler());

        server.createContext("/api/register", new ApiRegisterHandler(ctx));
        server.createContext("/api/login", new ApiLoginHandler(ctx));
        server.createContext("/api/logout", new ApiLogoutHandler(ctx));
        server.createContext("/api/me", new ApiMeHandler(ctx));
        server.createContext("/api/ask", new ApiAskHandler(ctx));

        server.createContext("/api/admin/qa-logs", new AdminQaLogsHandler(ctx));
        server.createContext("/api/admin/task-history", new AdminTaskHistoryHandler(ctx));
        server.createContext("/api/admin/import/batch", new AdminImportBatchHandler(ctx));
        server.createContext("/api/admin/permission/grant", new AdminGrantPermissionHandler(ctx));
        server.createContext("/api/admin/permission/revoke", new AdminRevokePermissionHandler(ctx));
        server.createContext("/api/admin/permission/list", new AdminListPermissionHandler(ctx));

        server.createContext("/api/admin/kb/laws", new AdminKbLawsHandler(ctx));
        server.createContext("/api/admin/kb/articles", new AdminKbArticlesHandler(ctx));
        server.createContext("/api/admin/kb/article", new AdminKbArticleHandler(ctx));
        server.createContext("/api/admin/kb/delete/article", new AdminKbDeleteArticleHandler(ctx));
        server.createContext("/api/admin/kb/delete/law", new AdminKbDeleteLawHandler(ctx));
        server.createContext("/api/admin/kb/rebuild/article", new AdminKbRebuildArticleHandler(ctx));
        server.createContext("/api/admin/kb/rebuild/law", new AdminKbRebuildLawHandler(ctx));
        server.createContext("/api/admin/kb/quality", new AdminKbQualityHandler(ctx));
    }

    private record AuthInfo(String sessionId, UserAccount user) {
    }

    private static AuthInfo authenticate(WebAppContext ctx, HttpExchange exchange) {
        Map<String, String> cookies = WebUtil.parseCookies(exchange);
        String sid = cookies.get(COOKIE_SESSION_ID);
        if (sid == null || sid.isBlank()) {
            return null;
        }
        UserAccount user = ctx.getAuthService().authenticate(sid);
        if (user == null) {
            return null;
        }
        return new AuthInfo(sid, user);
    }

    private static boolean isSuperAdmin(UserAccount user) {
        return user != null && user.getRole() == UserRole.SUPER_ADMIN;
    }

    private static boolean isTruthy(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("on");
    }

    private static void json(HttpExchange exchange, int status, Map<String, ?> payload) throws IOException {
        WebUtil.sendText(exchange, status, "application/json; charset=utf-8", JsonUtil.obj(payload));
    }

    private static void jsonText(HttpExchange exchange, int status, String json) throws IOException {
        WebUtil.sendText(exchange, status, "application/json; charset=utf-8", json == null ? "{}" : json);
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        WebUtil.sendText(exchange, 404, "text/plain; charset=utf-8", "404 NOT FOUND");
    }

    private static void methodNotAllowed(HttpExchange exchange) throws IOException {
        WebUtil.sendText(exchange, 405, "text/plain; charset=utf-8", "405 METHOD NOT ALLOWED");
    }

    private static void forbid(HttpExchange exchange) throws IOException {
        WebUtil.sendText(exchange, 403, "text/plain; charset=utf-8", "403 FORBIDDEN");
    }

    private static byte[] readResourceBytes(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        try (InputStream in = LegalWebHandlers.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private static String contentTypeByName(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String n = name.toLowerCase();
        if (n.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (n.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (n.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (n.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (n.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static void servePage(HttpExchange exchange, String resourcePath) throws IOException {
        byte[] bytes = readResourceBytes(resourcePath);
        if (bytes == null) {
            notFound(exchange);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String first(Map<String, String> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        String v = map.get(key);
        return v == null ? null : v.trim();
    }

    private static List<Map<String, Object>> qaLogsToJson(List<QaLog> logs) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (logs == null) {
            return list;
        }
        for (QaLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("userId", l.getUserId());
            m.put("sessionId", l.getSessionId());
            m.put("question", l.getQuestion());
            m.put("answer", l.getAnswer());
            m.put("agentState", l.getAgentState());
            m.put("status", l.getStatus());
            m.put("errorMessage", l.getErrorMessage());
            m.put("createdAt", l.getCreatedAt() == null ? null : TS_FMT.format(l.getCreatedAt().toInstant()));
            list.add(m);
        }
        return list;
    }

    private static List<Map<String, Object>> taskHistoryToJson(List<AgentTaskHistory> logs) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (logs == null) {
            return list;
        }
        for (AgentTaskHistory h : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("sessionId", h.getSessionId());
            m.put("userQuery", h.getUserQuery());
            m.put("intentType", h.getIntentType());
            m.put("strategyUsed", h.getStrategyUsed());
            m.put("resultStatus", h.getResultStatus());
            m.put("failReason", h.getFailReason());
            m.put("createdAt", h.getCreatedAt() == null ? null : TS_FMT.format(h.getCreatedAt().toInstant()));
            list.add(m);
        }
        return list;
    }

    private static Map<String, Object> importBatchResultToMap(LawDocumentImportBatchResult r) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("totalFiles", r.getTotalFiles());
        root.put("successFiles", r.getSuccessFiles());
        root.put("failedFiles", r.getFailedFiles());
        root.put("skippedFiles", r.getSkippedFiles());
        root.put("parsedLawTexts", r.getParsedLawTexts());
        root.put("insertedLawTexts", r.getInsertedLawTexts());
        root.put("failedLawTexts", r.getFailedLawTexts());
        root.put("elapsedMs", r.getElapsedMs());
        List<Map<String, Object>> fileResults = new ArrayList<>();
        for (LawDocumentImportFileResult fr : r.getFileResults()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", fr.getPath());
            m.put("status", fr.getStatus() == null ? null : fr.getStatus().name());
            m.put("message", fr.getMessage());
            m.put("parsedLawTexts", fr.getParsedLawTexts());
            m.put("insertedLawTexts", fr.getInsertedLawTexts());
            m.put("failedLawTexts", fr.getFailedLawTexts());
            m.put("elapsedMs", fr.getElapsedMs());
            fileResults.add(m);
        }
        root.put("fileResults", fileResults);
        return root;
    }

    private static final class RootHandler implements HttpHandler {
        private final WebAppContext ctx;

        private RootHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                WebUtil.redirect(exchange, "/login");
                return;
            }
            if (isSuperAdmin(auth.user())) {
                WebUtil.redirect(exchange, "/admin");
            } else {
                WebUtil.redirect(exchange, "/app");
            }
        }
    }

    private static final class LoginPageHandler implements HttpHandler {
        private final WebAppContext ctx;

        private LoginPageHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth != null) {
                WebUtil.redirect(exchange, isSuperAdmin(auth.user()) ? "/admin" : "/app");
                return;
            }
            servePage(exchange, "web/login.html");
        }
    }

    private static final class RegisterPageHandler implements HttpHandler {
        private final WebAppContext ctx;

        private RegisterPageHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth != null) {
                WebUtil.redirect(exchange, isSuperAdmin(auth.user()) ? "/admin" : "/app");
                return;
            }
            servePage(exchange, "web/register.html");
        }
    }

    private static final class AppPageHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AppPageHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                WebUtil.redirect(exchange, "/login");
                return;
            }
            servePage(exchange, "web/app.html");
        }
    }

    private static final class AdminPageHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminPageHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                WebUtil.redirect(exchange, "/login");
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                forbid(exchange);
                return;
            }
            servePage(exchange, "web/admin.html");
        }
    }

    private static final class StaticAssetsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path == null || !path.startsWith("/assets/")) {
                notFound(exchange);
                return;
            }
            String name = path.substring("/assets/".length());
            if (name.isEmpty() || name.contains("..") || name.contains("\\") || name.startsWith("/")) {
                notFound(exchange);
                return;
            }
            byte[] bytes = readResourceBytes("web/assets/" + name);
            if (bytes == null) {
                notFound(exchange);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", contentTypeByName(name));
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static final class ApiRegisterHandler implements HttpHandler {
        private final WebAppContext ctx;

        private ApiRegisterHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String username = first(form, "username");
            String password = first(form, "password");
            try {
                UserAccount u = ctx.getAuthService().register(username, password);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("userId", u.getId());
                resp.put("username", u.getUsername());
                json(exchange, 200, resp);
            } catch (Exception e) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", false);
                resp.put("message", e.getMessage() == null ? "register_failed" : e.getMessage());
                json(exchange, 400, resp);
            }
        }
    }

    private static final class ApiLoginHandler implements HttpHandler {
        private final WebAppContext ctx;

        private ApiLoginHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String username = first(form, "username");
            String password = first(form, "password");
            LoginResult lr;
            try {
                lr = ctx.getAuthService().login(username, password);
            } catch (Exception e) {
                lr = LoginResult.fail(e.getMessage() == null ? "login_failed" : e.getMessage());
            }
            if (!lr.isSuccess()) {
                json(exchange, 401, Map.of("success", false, "message", lr.getMessage()));
                return;
            }
            // 7 天 cookie（服务端会话仍以 DB 为准）
            WebUtil.setCookie(exchange, COOKIE_SESSION_ID, lr.getSessionId(), 86400 * 7, true);
            String role = lr.getUser() == null || lr.getUser().getRole() == null ? "USER" : lr.getUser().getRole().name();
            String redirect = "SUPER_ADMIN".equals(role) ? "/admin" : "/app";
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("sessionId", lr.getSessionId());
            resp.put("redirect", redirect);
            resp.put("role", role);
            resp.put("user", Map.of(
                    "id", lr.getUser() == null ? null : lr.getUser().getId(),
                    "username", lr.getUser() == null ? null : lr.getUser().getUsername()
            ));
            resp.put("permissions", lr.getPermissions());
            json(exchange, 200, resp);
        }
    }

    private static final class ApiLogoutHandler implements HttpHandler {
        private final WebAppContext ctx;

        private ApiLogoutHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            Map<String, String> cookies = WebUtil.parseCookies(exchange);
            String sid = cookies.get(COOKIE_SESSION_ID);
            if (sid != null && !sid.isBlank()) {
                try {
                    ctx.getAuthService().logout(sid);
                } catch (Exception ignored) {
                    // 日志失败不影响主流程
                }
            }
            WebUtil.setCookie(exchange, COOKIE_SESSION_ID, "", 0, true);
            json(exchange, 200, Map.of("success", true));
        }
    }

    private static final class ApiMeHandler implements HttpHandler {
        private final WebAppContext ctx;

        private ApiMeHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            List<PermissionCode> perms = ctx.getAuthService().resolveEffectivePermissions(auth.user());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("sessionId", auth.sessionId());
            resp.put("user", Map.of(
                    "id", auth.user().getId(),
                    "username", auth.user().getUsername(),
                    "role", auth.user().getRole() == null ? null : auth.user().getRole().name()
            ));
            resp.put("permissions", perms);
            json(exchange, 200, resp);
        }
    }

    private static final class ApiAskHandler implements HttpHandler {
        private final WebAppContext ctx;

        private ApiAskHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String question = first(form, "question");
            String historyFacts = first(form, "historyFacts");
            if (question == null || question.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "question_required"));
                return;
            }
            // 权限预留：非 SUPER_ADMIN 的用户至少需要 QA_ASK
            if (!isSuperAdmin(auth.user())) {
                List<PermissionCode> perms = ctx.getAuthService().resolveEffectivePermissions(auth.user());
                // 兼容早期数据：若未配置任何权限，默认允许提问
                if (!perms.isEmpty() && !perms.contains(PermissionCode.QA_ASK)) {
                    json(exchange, 403, Map.of("success", false, "message", "NO_PERMISSION:QA_ASK"));
                    return;
                }
            }
            try {
                String answer = ctx.getAgentService().answer(auth.user().getId(), auth.sessionId(), question, historyFacts == null ? "" : historyFacts);
                json(exchange, 200, Map.of("success", true, "answer", answer));
            } catch (Exception e) {
                json(exchange, 500, Map.of("success", false, "message", e.getMessage() == null ? "ask_failed" : e.getMessage()));
            }
        }
    }

    private static final class AdminQaLogsHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminQaLogsHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> q = WebUtil.parseQuery(exchange.getRequestURI().getQuery());
            int limit = WebUtil.parseInt(q.get("limit"), 50);
            String sessionId = first(q, "sessionId");
            List<QaLog> logs = (sessionId == null || sessionId.isBlank())
                    ? ctx.getQaLogDao().findRecent(limit)
                    : ctx.getQaLogDao().findRecentBySession(sessionId, limit);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("logs", qaLogsToJson(logs));
            jsonText(exchange, 200, JsonUtil.obj(resp));
        }
    }

    private static final class AdminTaskHistoryHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminTaskHistoryHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> q = WebUtil.parseQuery(exchange.getRequestURI().getQuery());
            int limit = WebUtil.parseInt(q.get("limit"), 50);
            String sessionId = first(q, "sessionId");
            List<AgentTaskHistory> logs = (sessionId == null || sessionId.isBlank())
                    ? ctx.getTaskHistoryDao().findRecent(limit)
                    : ctx.getTaskHistoryDao().findRecentBySession(sessionId, limit);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("logs", taskHistoryToJson(logs));
            jsonText(exchange, 200, JsonUtil.obj(resp));
        }
    }

    private static final class AdminImportBatchHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminImportBatchHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String rawPaths = first(form, "paths");
            if (rawPaths == null || rawPaths.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "paths_required"));
                return;
            }
            List<String> paths = new ArrayList<>();
            for (String line : rawPaths.split("\\r?\\n")) {
                String p = line == null ? "" : line.trim();
                if (!p.isEmpty()) {
                    paths.add(p);
                }
            }
            if (paths.isEmpty()) {
                json(exchange, 400, Map.of("success", false, "message", "paths_required"));
                return;
            }

            LawDocumentBatchImportRequest req = new LawDocumentBatchImportRequest(paths);
            req.setRecursive(isTruthy(first(form, "recursive")));
            req.setSkipIfProcessed(!isTruthy(first(form, "noSkip")));
            req.setChunkSize(WebUtil.parseInt(first(form, "chunkSize"), 400));

            try {
                LawDocumentImportBatchResult r = ctx.getImportService().importBatch(req);
                json(exchange, 200, Map.of("success", true, "result", importBatchResultToMap(r)));
            } catch (Exception e) {
                json(exchange, 500, Map.of("success", false, "message", e.getMessage() == null ? "import_failed" : e.getMessage()));
            }
        }
    }

    private static final class AdminGrantPermissionHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminGrantPermissionHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String username = first(form, "username");
            String perm = first(form, "permission");
            if (username == null || username.isBlank() || perm == null || perm.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "username_and_permission_required"));
                return;
            }
            PermissionCode code;
            try {
                code = PermissionCode.valueOf(perm.trim());
            } catch (Exception e) {
                json(exchange, 400, Map.of("success", false, "message", "unknown_permission:" + perm));
                return;
            }
            try {
                boolean ok = ctx.getAdminService().grantPermission(auth.user(), username, code);
                json(exchange, 200, Map.of("success", ok));
            } catch (Exception e) {
                json(exchange, 400, Map.of("success", false, "message", e.getMessage() == null ? "grant_failed" : e.getMessage()));
            }
        }
    }

    private static final class AdminRevokePermissionHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminRevokePermissionHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String username = first(form, "username");
            String perm = first(form, "permission");
            if (username == null || username.isBlank() || perm == null || perm.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "username_and_permission_required"));
                return;
            }
            PermissionCode code;
            try {
                code = PermissionCode.valueOf(perm.trim());
            } catch (Exception e) {
                json(exchange, 400, Map.of("success", false, "message", "unknown_permission:" + perm));
                return;
            }
            try {
                boolean ok = ctx.getAdminService().revokePermission(auth.user(), username, code);
                json(exchange, 200, Map.of("success", ok));
            } catch (Exception e) {
                json(exchange, 400, Map.of("success", false, "message", e.getMessage() == null ? "revoke_failed" : e.getMessage()));
            }
        }
    }

    private static final class AdminListPermissionHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminListPermissionHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> q = WebUtil.parseQuery(exchange.getRequestURI().getQuery());
            String username = first(q, "username");
            if (username == null || username.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "username_required"));
                return;
            }
            try {
                List<PermissionCode> perms = ctx.getAdminService().listDirectPermissions(auth.user(), username);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("permissions", perms);
                json(exchange, 200, resp);
            } catch (Exception e) {
                json(exchange, 400, Map.of("success", false, "message", e.getMessage() == null ? "list_failed" : e.getMessage()));
            }
        }
    }

    private static final class AdminKbLawsHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbLawsHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> q = WebUtil.parseQuery(exchange.getRequestURI().getQuery());
            int limit = WebUtil.parseInt(q.get("limit"), 100);
            List<LawCodeSummary> summaries = ctx.getLawTextDao().listLawSummaries(limit);
            List<Map<String, Object>> out = new ArrayList<>();
            for (LawCodeSummary s : summaries) {
                out.add(Map.of(
                        "lawCode", s.getLawCode(),
                        "lawTitleSample", s.getLawTitleSample(),
                        "articleCount", s.getArticleCount(),
                        "chunkCount", s.getChunkCount(),
                        "vectorCount", s.getVectorCount()
                ));
            }
            json(exchange, 200, Map.of("success", true, "laws", out));
        }
    }

    private static final class AdminKbArticlesHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbArticlesHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> q = WebUtil.parseQuery(exchange.getRequestURI().getQuery());
            String lawCode = first(q, "lawCode");
            if (lawCode == null || lawCode.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "lawCode_required"));
                return;
            }
            int limit = WebUtil.parseInt(q.get("limit"), 50);
            int offset = WebUtil.parseInt(q.get("offset"), 0);
            List<LawText> list = ctx.getLawTextDao().findArticlesByLawCodeLike(lawCode, limit, offset);
            List<Map<String, Object>> out = new ArrayList<>();
            for (LawText t : list) {
                out.add(Map.of(
                        "id", t.getId(),
                        "lawCode", t.getLawCode(),
                        "lawTitle", t.getLawTitle(),
                        "articleNumber", t.getArticleNumber()
                ));
            }
            json(exchange, 200, Map.of("success", true, "articles", out));
        }
    }

    private static final class AdminKbArticleHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbArticleHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> q = WebUtil.parseQuery(exchange.getRequestURI().getQuery());
            long id = WebUtil.parseLong(q.get("id"), -1L);
            if (id <= 0) {
                json(exchange, 400, Map.of("success", false, "message", "id_required"));
                return;
            }
            LawText t = ctx.getLawTextDao().findById(id);
            if (t == null) {
                json(exchange, 404, Map.of("success", false, "message", "NOT_FOUND"));
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("article", Map.of(
                    "id", t.getId(),
                    "lawCode", t.getLawCode(),
                    "lawTitle", t.getLawTitle(),
                    "articleNumber", t.getArticleNumber(),
                    "fullText", t.getFullText()
            ));
            jsonText(exchange, 200, JsonUtil.obj(out));
        }
    }

    private static final class AdminKbDeleteArticleHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbDeleteArticleHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            long id = WebUtil.parseLong(first(form, "id"), -1L);
            if (id <= 0) {
                json(exchange, 400, Map.of("success", false, "message", "id_required"));
                return;
            }
            Map<String, Object> r = ctx.getKbMaintenanceService().deleteArticle(id);
            jsonText(exchange, 200, JsonUtil.obj(r));
        }
    }

    private static final class AdminKbDeleteLawHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbDeleteLawHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String lawCode = first(form, "lawCode");
            if (lawCode == null || lawCode.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "lawCode_required"));
                return;
            }
            Map<String, Object> r = ctx.getKbMaintenanceService().deleteLawByLawCode(lawCode);
            jsonText(exchange, 200, JsonUtil.obj(r));
        }
    }

    private static final class AdminKbRebuildArticleHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbRebuildArticleHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            long id = WebUtil.parseLong(first(form, "id"), -1L);
            int chunkSize = WebUtil.parseInt(first(form, "chunkSize"), 400);
            if (id <= 0) {
                json(exchange, 400, Map.of("success", false, "message", "id_required"));
                return;
            }
            Map<String, Object> r = ctx.getKbMaintenanceService().rebuildArticle(id, chunkSize);
            jsonText(exchange, 200, JsonUtil.obj(r));
        }
    }

    private static final class AdminKbRebuildLawHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbRebuildLawHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> form = WebUtil.parseForm(WebUtil.readBody(exchange));
            String lawCode = first(form, "lawCode");
            int chunkSize = WebUtil.parseInt(first(form, "chunkSize"), 400);
            if (lawCode == null || lawCode.isBlank()) {
                json(exchange, 400, Map.of("success", false, "message", "lawCode_required"));
                return;
            }
            Map<String, Object> r = ctx.getKbMaintenanceService().rebuildLawByLawCode(lawCode, chunkSize);
            jsonText(exchange, 200, JsonUtil.obj(r));
        }
    }

    private static final class AdminKbQualityHandler implements HttpHandler {
        private final WebAppContext ctx;

        private AdminKbQualityHandler(WebAppContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                methodNotAllowed(exchange);
                return;
            }
            AuthInfo auth = authenticate(ctx, exchange);
            if (auth == null) {
                json(exchange, 401, Map.of("success", false, "message", "UNAUTHORIZED"));
                return;
            }
            if (!isSuperAdmin(auth.user())) {
                json(exchange, 403, Map.of("success", false, "message", "FORBIDDEN"));
                return;
            }
            Map<String, String> q = WebUtil.parseQuery(exchange.getRequestURI().getQuery());
            String lawCode = first(q, "lawCode");
            Map<String, Object> r = ctx.getKbQualityService().checkLawCode(lawCode);
            jsonText(exchange, 200, JsonUtil.obj(r));
        }
    }
}
