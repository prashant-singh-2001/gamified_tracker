package com.tracker.gateway.user;

import jakarta.persistence.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@ToString
@EqualsAndHashCode
@Table(name = "user_entity", uniqueConstraints = @UniqueConstraint(columnNames = {"email"}))
public class User {

    @Id
    @GeneratedValue
    private Long id;

    private String firstName;
    private String lastName;

    private String email;
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;
}
