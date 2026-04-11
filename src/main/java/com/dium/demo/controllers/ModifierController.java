package com.dium.demo.controllers;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.dto.responses.ModifierResponse;
import com.dium.demo.services.ModifierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modifiers")
public class ModifierController {
    private final ModifierService modifierService;

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PutMapping("/groups/{groupId}")
    public ResponseEntity<ModifierGroupResponse> updateGroup(@PathVariable Long groupId,
                                                             @Valid @RequestBody ModifierGroupRequest request) {
        return ResponseEntity.ok(modifierService.editModifierGroup(groupId, request));
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PutMapping("/{modifierId}")
    public ResponseEntity<ModifierResponse> updateModifier(@PathVariable Long modifierId,
                                                           @Valid @RequestBody ModifierRequest request) {
        return ResponseEntity.ok(modifierService.editModifier(modifierId, request));
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> deleteModifierGroup(@PathVariable Long groupId) {
        modifierService.deleteModifierGroup(groupId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PatchMapping("/groups/{groupId}/toggleRequired")
    public ResponseEntity<Void> toggle(@PathVariable Long groupId) {
        modifierService.toggleRequired(groupId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @DeleteMapping("/{modifierId}")
    public ResponseEntity<Void> deleteModifier(@PathVariable Long modifierId) {
        modifierService.delete(modifierId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PatchMapping("/{modifierId}")
    public ResponseEntity<Void> toggleStock(@PathVariable Long modifierId) {
        modifierService.toggleStock(modifierId);
        return ResponseEntity.ok().build();
    }

}
