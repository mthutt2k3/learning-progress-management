//package com.learning.progress.service;
//
//import com.learning.progress.common.RoleName;
//import com.learning.progress.common.UserStatus;
//import com.learning.progress.dto.response.UserProfileResponse;
//import com.learning.progress.entity.Role;
//import com.learning.progress.entity.User;
//import com.learning.progress.repository.UserRepository;
//import com.learning.progress.service.impl.UserServiceImpl;
//import com.learning.progress.util.JwtUtil;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//
//import java.time.LocalDate;
//import java.util.Date;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//class UserServiceImplTest {
//
//    @Mock
//    private UserRepository userRepository;
//
//    @Mock
//    private JwtUtil jwtUtil;
//
//    @InjectMocks
//    private UserServiceImpl userService;
//
//    private User mockUser;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//
//        Role role = new Role();
//        role.setName(RoleName.valueOf("ADMIN"));
//
//        mockUser = new User();
//        mockUser.setUserName("john_doe");
//        mockUser.setFirstName("John");
//        mockUser.setLastName("Doe");
//        mockUser.setEmail("john@example.com");
//        mockUser.setPhoneNumber("0123456789");
//        mockUser.setDateOfBirth(new Date(1990, 10, 1));
//        mockUser.setGender("Male");
//        mockUser.setStatus(UserStatus.ACTIVE);
//        mockUser.setAvatarUrl("https://example.com/avatar.jpg");
//        mockUser.setRole(role);
//    }
//
//    @Test
//    void getCurrentUserProfile_shouldReturnUserProfile_whenUserExists() {
//        // given
//        when(jwtUtil.extractUsernameFromCurrentRequest()).thenReturn("john_doe");
//        when(userRepository.findByUserName("john_doe")).thenReturn(Optional.of(mockUser));
//
//        // when
//        UserProfileResponse response = userService.getCurrentUserProfile();
//
//        // then
//        assertNotNull(response);
//        assertEquals("john_doe", response.getUsername());
//        assertEquals("John", response.getFirstName());
//        assertEquals("Doe", response.getLastName());
//        assertEquals("john@example.com", response.getEmail());
//        assertEquals("ADMIN", response.getRole());
//
//        verify(jwtUtil, times(1)).extractUsernameFromCurrentRequest();
//        verify(userRepository, times(1)).findByUserName("john_doe");
//    }
//
//    @Test
//    void getCurrentUserProfile_shouldThrowException_whenUserNotFound() {
//        // given
//        when(jwtUtil.extractUsernameFromCurrentRequest()).thenReturn("unknown_user");
//        when(userRepository.findByUserName("unknown_user")).thenReturn(Optional.empty());
//
//        // when + then
//        RuntimeException exception = assertThrows(RuntimeException.class, () ->
//                userService.getCurrentUserProfile());
//
//        assertEquals("User not found", exception.getMessage());
//        verify(jwtUtil, times(1)).extractUsernameFromCurrentRequest();
//    }
//}
//
