package br.com.agendafacilpro.web.form;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

public class WeeklyBusinessHoursForm {
    private List<DayHours> hours = new ArrayList<>();

    public List<DayHours> getHours() { return hours; }
    public void setHours(List<DayHours> hours) { this.hours = hours; }

    public static class DayHours {
        private DayOfWeek dayOfWeek;
        private boolean open;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime openingTime;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime closingTime;

        public DayOfWeek getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        public boolean isOpen() { return open; }
        public void setOpen(boolean open) { this.open = open; }
        public LocalTime getOpeningTime() { return openingTime; }
        public void setOpeningTime(LocalTime openingTime) { this.openingTime = openingTime; }
        public LocalTime getClosingTime() { return closingTime; }
        public void setClosingTime(LocalTime closingTime) { this.closingTime = closingTime; }
    }
}
