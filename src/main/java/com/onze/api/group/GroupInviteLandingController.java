package com.onze.api.group;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroupInviteLandingController {

    private static final Pattern INVITE_CODE = Pattern.compile("[A-Z2-9]{8}");

    private final GroupInviteService groupInviteService;

    public GroupInviteLandingController(GroupInviteService groupInviteService) {
        this.groupInviteService = groupInviteService;
    }

    @GetMapping(value = "/join/{rawCode}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> openInvite(@PathVariable String rawCode) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!INVITE_CODE.matcher(code).matches() || !groupInviteService.inviteExists(code)) {
            return ResponseEntity.status(404)
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.TEXT_HTML)
                    .body(invalidInvitePage());
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(invitePage(code));
    }

    private String invitePage(String code) {
        String appLink = "onze://join/" + code;
        return """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="theme-color" content="#148A4A">
                  <title>Convite para o Onze</title>
                  <style>
                    *{box-sizing:border-box}body{margin:0;background:#F4F7F5;color:#18221D;font-family:Arial,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}.card{width:min(460px,100%%);background:white;border:1px solid #DDE6E0;border-radius:24px;padding:32px;text-align:center;box-shadow:0 12px 35px rgba(0,0,0,.08)}.brand{color:#148A4A;font-size:22px;font-weight:900;letter-spacing:2px}.ball{font-size:50px;margin:14px 0}.title{font-size:28px;font-weight:800;margin:0 0 10px}.text{color:#607068;line-height:1.5;margin:0 0 22px}.code-label{font-size:12px;font-weight:700;color:#607068;letter-spacing:1px}.code{font-size:34px;font-weight:900;color:#148A4A;letter-spacing:4px;margin:8px 0 24px}.button{display:block;text-decoration:none;background:#148A4A;color:white;font-weight:800;border-radius:14px;padding:16px 20px;font-size:17px}.hint{font-size:13px;color:#607068;line-height:1.45;margin:18px 0 0}
                  </style>
                </head>
                <body>
                  <main class="card">
                    <div class="brand">ONZE</div>
                    <div class="ball">⚽</div>
                    <h1 class="title">Você recebeu um convite</h1>
                    <p class="text">Abra o Onze para entrar no grupo. Se o aplicativo pedir login ou cadastro, o convite continuará disponível.</p>
                    <div class="code-label">CÓDIGO DO GRUPO</div>
                    <div class="code">%s</div>
                    <a class="button" href="%s">Abrir no Onze</a>
                    <p class="hint">Se o aplicativo ainda não estiver instalado ou não abrir automaticamente, guarde o código acima e informe-o na opção “Entrar em grupo”.</p>
                  </main>
                </body>
                </html>
                """.formatted(code, appLink);
    }

    private String invalidInvitePage() {
        return """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="theme-color" content="#148A4A">
                  <title>Convite inválido - Onze</title>
                  <style>*{box-sizing:border-box}body{margin:0;background:#F4F7F5;color:#18221D;font-family:Arial,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}.card{width:min(460px,100%);background:white;border:1px solid #DDE6E0;border-radius:24px;padding:32px;text-align:center}.brand{color:#148A4A;font-size:22px;font-weight:900;letter-spacing:2px}h1{font-size:26px}p{color:#607068;line-height:1.5}</style>
                </head>
                <body><main class="card"><div class="brand">ONZE</div><h1>Este convite não é mais válido</h1><p>Peça ao administrador do grupo um novo link ou código de convite.</p></main></body>
                </html>
                """;
    }
}
