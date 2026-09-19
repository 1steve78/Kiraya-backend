package com.hyperlocal.auth.security;

import com.hyperlocal.auth.service.CustomUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private  final JwtService jwtService;
    private  final CustomUserDetailsService userDetailsService;

    public  JwtAuthenticationFilter(JwtService jwtService , CustomUserDetailsService userDetailsService){
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
            ) throws  IOException, ServletException {

        final  String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            jwt = authHeader.substring(7);
            userEmail = jwtService.extractUsername(jwt);

            if(userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null){

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if(jwtService.isTokenValid(jwt,userDetails)){

                    String role = jwtService.extractClaim(jwt, claims -> claims.get("role", String.class));

                    List<SimpleGrantedAuthority> authorities;
                    if (role != null) {
                        authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
                    } else {
                        // Fallback just in case an old token without a role claim is used
                        authorities = (List<SimpleGrantedAuthority>) userDetails.getAuthorities();
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            authorities
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);

                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }

        filterChain.doFilter(request,response);
    }


}
