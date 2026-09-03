package com.howl.uwtracker.web;

import com.howl.uwtracker.domain.ModuleType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets {@code @RequestParam ModuleType type} bind the lowercase wire value ({@code plugin} /
 * {@code module}) — Spring's default enum binding is {@code Enum.valueOf} (case-sensitive), which
 * wouldn't match. Auto-registered into the MVC conversion service as a {@code Converter} bean.
 */
@Component
public class StringToModuleTypeConverter implements Converter<String, ModuleType> {

    @Override
    public ModuleType convert(String source) {
        return ModuleType.fromWire(source);
    }
}
