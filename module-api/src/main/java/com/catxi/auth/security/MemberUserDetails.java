package com.catxi.auth.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class MemberUserDetails implements UserDetails {
  private final String email;
  private final String password;
  private final Collection<? extends GrantedAuthority> authorities;

  public MemberUserDetails(String email, String password, String role) {
    this.email = email;
    this.password = password;
    String authority = role == null || role.isBlank() ? "ROLE_USER" : role;
    this.authorities = List.of(new SimpleGrantedAuthority(authority));
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
