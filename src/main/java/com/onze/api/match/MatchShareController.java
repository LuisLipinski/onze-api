package com.onze.api.match;

import java.util.UUID;

import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchShareController {
    private final FootballMatchRepository matches;
    private final GroupMemberRepository members;
    private final String publicBaseUrl;

    public MatchShareController(FootballMatchRepository matches, GroupMemberRepository members,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this.matches = matches;
        this.members = members;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/api/matches/{matchId}/share")
    public ShareResponse share(Authentication authentication, @PathVariable UUID matchId) {
        FootballMatch match = matches.findById(matchId).orElseThrow(MatchService.MatchNotFoundException::new);
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
        members.findByGroupIdAndUserId(match.getGroupId(), userId)
                .orElseThrow(GroupAccessDeniedException::new);
        return new ShareResponse(publicBaseUrl + "/m/" + matchId);
    }

    @GetMapping(value = "/m/{matchId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> open(@PathVariable UUID matchId) {
        boolean exists = matches.existsById(matchId);
        String action = exists
                ? "<a class=\"button\" href=\"onze://match?matchId=" + matchId + "\">Abrir no Onze</a>"
                : "";
        String title = exists ? "Você recebeu um convite para um jogo" : "Jogo não encontrado";
        String text = exists
                ? "Abra o Onze para ver o jogo e responder se vai jogar. Entre na sua conta se o aplicativo pedir login."
                : "Peça a quem compartilhou que confira o convite.";
        String page = """
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="theme-color" content="#148A4A"><title>Convite para um jogo - Onze</title>
                <style>*{box-sizing:border-box}body{margin:0;background:#F4F7F5;color:#18221D;font-family:Arial,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}.card{width:min(460px,100%%);background:white;border:1px solid #DDE6E0;border-radius:24px;padding:32px;text-align:center}.brand{color:#148A4A;font-size:22px;font-weight:900}h1{font-size:27px}p{color:#607068;line-height:1.5}.button{display:block;text-decoration:none;background:#148A4A;color:white;font-weight:800;border-radius:14px;padding:16px;font-size:17px}.hint{font-size:13px}</style>
                </head><body><main class="card"><div class="brand">ONZE ⚽</div>
                <h1>%s</h1><p>%s</p>%s
                <p class="hint">O aplicativo precisa estar instalado. O acesso ao jogo continua restrito aos membros do grupo; este link não inclui você automaticamente.</p>
                </main></body></html>
                """.formatted(title, text, action);
        return ResponseEntity.status(exists ? 200 : 404).cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML).body(page);
    }

    public record ShareResponse(String shareUrl) { }
}
