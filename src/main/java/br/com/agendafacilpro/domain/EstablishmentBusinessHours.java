package br.com.agendafacilpro.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.*;

@Entity
@Table(name = "establishment_business_hours",
        uniqueConstraints = @UniqueConstraint(name = "uk_business_hours_est_day", columnNames = {"establishment_id", "day_of_week"}))
public class EstablishmentBusinessHours {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Establishment establishment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    private LocalTime openingTime;
    private LocalTime closingTime;

    public Long getId() { return id; }
    public Establishment getEstablishment() { return establishment; }
    public void setEstablishment(Establishment establishment) { this.establishment = establishment; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public LocalTime getOpeningTime() { return openingTime; }
    public void setOpeningTime(LocalTime openingTime) { this.openingTime = openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
    public void setClosingTime(LocalTime closingTime) { this.closingTime = closingTime; }
    public String getDayLabel() {
        return switch (dayOfWeek) {
            case MONDAY -> "Segunda-feira";
            case TUESDAY -> "Terça-feira";
            case WEDNESDAY -> "Quarta-feira";
            case THURSDAY -> "Quinta-feira";
            case FRIDAY -> "Sexta-feira";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}
