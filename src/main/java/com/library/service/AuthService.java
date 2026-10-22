package com.library.service;

import com.library.dto.AuthRequest;
import com.library.dto.AuthResponse;
import com.library.dto.RegisterRequest;
import com.library.exception.BadRequestException;
import com.library.exception.ConflictException;
import com.library.exception.ResourceNotFoundException;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.Member;
import com.library.model.Role;
import com.library.repository.MemberRepository;
import com.library.security.JwtTokenProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final IdGeneratorService idGeneratorService;

    public AuthService(MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       IdGeneratorService idGeneratorService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.idGeneratorService = idGeneratorService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered: " + email);
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        Role role = request.getRole() != null ? request.getRole() : Role.MEMBER;

        // Collision-resistant ID generation with retry
        Member savedMember = null;
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            String memberId = idGeneratorService.generateMemberId();
            Member member = new Member(memberId, request.getName().trim(), email, passwordHash, role, request.getPhone());
            try {
                savedMember = memberRepository.save(member);
                break;
            } catch (DuplicateKeyException ex) {
                if (i == maxRetries - 1) {
                    throw new ConflictException("Failed to generate unique member ID after retries: " + ex.getMessage());
                }
            }
        }

        String token = tokenProvider.generateToken(savedMember);
        return new AuthResponse(token, savedMember.getMemberId(), savedMember.getName(), savedMember.getEmail(), savedMember.getRole());
    }

    public AuthResponse login(AuthRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Member member = memberRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), member.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(member);
        return new AuthResponse(token, member.getMemberId(), member.getName(), member.getEmail(), member.getRole());
    }

    public Member getMemberProfile(String memberId) {
        return memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId));
    }
}
