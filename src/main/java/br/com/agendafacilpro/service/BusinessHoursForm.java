package br.com.agendafacilpro.service;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

public record BusinessHoursForm(
        DayOfWeek dayOfWeek,
        boolean open,
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime openingTime,
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime closingTime) {
}
