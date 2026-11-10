package com.dium.demo.services.product;

import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.responses.ModifierResponse;

public interface ModifierService {
    ModifierResponse editModifier(Long modifierId, ModifierRequest request);
    void delete(Long modifierId);
    void toggleStock(Long modifierId);
}
