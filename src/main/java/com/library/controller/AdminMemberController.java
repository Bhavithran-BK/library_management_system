package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.model.Member;
import com.library.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberController {

    private final MemberRepository memberRepository;

    public AdminMemberController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Member>>> getAllMembers() {
        List<Member> members = memberRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(members));
    }
}
