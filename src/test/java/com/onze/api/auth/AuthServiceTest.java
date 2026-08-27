package com.onze.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.onze.api.auth.AuthModels.LoginRequest;
import com.onze.api.auth.AuthModels.RegisterRequest;
import com.onze.api.auth.AuthService.EmailAlreadyInUseException;
import com.onze.api.auth.AuthService.InvalidCredentialsException;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Test
    void registersUserWithNormalizedEmailAndEncodedPassword() {
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenService);
        RegisterRequest request = new RegisterRequest(" User@Example.com ", "strongPass1", " Luis ");

        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strongPass1")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issueToken(any(User.class))).thenReturn(new TokenService.IssuedToken("jwt", 7200));

        var response = service.register(request);

        assertEquals("jwt", response.accessToken());
        assertEquals("user@example.com", response.user().email());
        assertEquals("Luis", response.user().displayName());
        verify(passwordEncoder).encode("strongPass1");
    }

    @Test
    void rejectsDuplicatedEmail() {
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenService);
        RegisterRequest request = new RegisterRequest("user@example.com", "strongPass1", "Luis");

        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class, () -> service.register(request));
    }

    @Test
    void logsInWithValidCredentials() {
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenService);
        User user = new User("user@example.com", "encoded-password", "Luis");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("strongPass1", "encoded-password")).thenReturn(true);
        when(tokenService.issueToken(user)).thenReturn(new TokenService.IssuedToken("jwt", 7200));

        var response = service.login(new LoginRequest("user@example.com", "strongPass1"));

        assertEquals("jwt", response.accessToken());
    }

    @Test
    void rejectsInvalidPassword() {
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenService);
        User user = new User("user@example.com", "encoded-password", "Luis");

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("user@example.com", "wrong-password")));
    }
}
