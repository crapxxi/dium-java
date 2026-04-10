package com.dium.demo.controllers;

import com.dium.demo.dto.product_modifier.ModifierDTO;
import com.dium.demo.dto.product_modifier.ModifierGroupDTO;
import com.dium.demo.services.ModifierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modifiers")
@Tag(name = "Modifiers")
public class ModifierController {
    private final ModifierService modifierService;

    @PutMapping("/groups/{groupId}")
    @Operation(summary = "update modifierGroup")
    public ResponseEntity<?> updateGroup(@AuthenticationPrincipal UserDetails userDetails,
                                    @PathVariable Long groupId,
                                    @RequestBody ModifierGroupDTO modifierGroupDTO) {
        return ResponseEntity.ok(modifierService.editModifierGroup(userDetails, groupId, modifierGroupDTO));
    }

    @PutMapping("/{modifierId}")
    @Operation(summary = "update modifier")
    public ResponseEntity<?> updateModifier(@AuthenticationPrincipal UserDetails userDetails,
                                            @PathVariable Long modifierId,
                                            @RequestBody ModifierDTO modifierDTO) {
        return ResponseEntity.ok(modifierService.editModifier(userDetails, modifierId, modifierDTO));
    }

    @DeleteMapping("/groups/{groupId}")
    @Operation(summary = "delete modifierGroup")
    public ResponseEntity<?> deleteModifierGroup(@AuthenticationPrincipal UserDetails userDetails,
                                                 @PathVariable Long groupId) {
        modifierService.deleteModifierGroup(userDetails, groupId);
        return ResponseEntity.ok().build();
    }
    @PatchMapping("/groups/{groupId}/toggleRequired")
    @Operation(summary = "toggle modifierGroup required")
    public ResponseEntity<?> toggle(@AuthenticationPrincipal UserDetails userDetails,
                                    @PathVariable Long groupId) {
        modifierService.toggleRequired(userDetails, groupId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{modifierId}")
    @Operation(summary = "delete modifier")
    public ResponseEntity<?> deleteModifier(@AuthenticationPrincipal UserDetails userDetails,
                                                 @PathVariable Long modifierId) {
        modifierService.delete(userDetails, modifierId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{modifierId}")
    @Operation(summary = "toggle stock")
    public ResponseEntity<?> toggleStock(@AuthenticationPrincipal UserDetails userDetails,
                                         @PathVariable Long modifierId) {
        modifierService.toggleStock(userDetails, modifierId);
        return ResponseEntity.ok().build();
    }

}
