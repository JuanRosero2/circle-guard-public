package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessPointServiceTest {

    @Mock
    private AccessPointRepository accessPointRepository;
    
    @Mock
    private FloorRepository floorRepository;
    
    @InjectMocks
    private AccessPointService accessPointService;
    
    private Floor testFloor;
    private UUID floorId;
    private UUID accessPointId;

    @BeforeEach
    void setUp() {
        floorId = UUID.randomUUID();
        accessPointId = UUID.randomUUID();
        testFloor = Floor.builder()
                .id(floorId)
                .name("Test Floor")
                .floorNumber(1)
                .build();
    }

    @Test
    void registerAccessPoint_ValidData_ReturnsSavedAccessPoint() {
        // Arrange
        String macAddress = "AA:BB:CC:DD:EE:FF";
        Double x = 10.5;
        Double y = 20.5;
        String name = "Test AP";
        
        AccessPoint expectedAccessPoint = AccessPoint.builder()
                .id(accessPointId)
                .floor(testFloor)
                .macAddress(macAddress)
                .coordinateX(x)
                .coordinateY(y)
                .name(name)
                .build();
        
        when(floorRepository.findById(floorId)).thenReturn(Optional.of(testFloor));
        when(accessPointRepository.save(any(AccessPoint.class))).thenReturn(expectedAccessPoint);

        // Act
        AccessPoint result = accessPointService.registerAccessPoint(floorId, macAddress, x, y, name);

        // Assert
        assertNotNull(result);
        assertEquals(accessPointId, result.getId());
        assertEquals(testFloor, result.getFloor());
        assertEquals(macAddress, result.getMacAddress());
        assertEquals(x, result.getCoordinateX());
        assertEquals(y, result.getCoordinateY());
        assertEquals(name, result.getName());
        
        verify(floorRepository).findById(floorId);
        verify(accessPointRepository).save(any(AccessPoint.class));
    }

    @Test
    void registerAccessPoint_NonExistentFloor_ThrowsRuntimeException() {
        // Arrange
        String macAddress = "AA:BB:CC:DD:EE:FF";
        Double x = 10.5;
        Double y = 20.5;
        String name = "Test AP";
        
        when(floorRepository.findById(floorId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            accessPointService.registerAccessPoint(floorId, macAddress, x, y, name);
        });
        
        verify(floorRepository).findById(floorId);
        verify(accessPointRepository, never()).save(any());
    }

    @Test
    void getAccessPoint_ExistingId_ReturnsAccessPoint() {
        // Arrange
        AccessPoint expectedAccessPoint = AccessPoint.builder()
                .id(accessPointId)
                .floor(testFloor)
                .macAddress("AA:BB:CC:DD:EE:FF")
                .coordinateX(10.5)
                .coordinateY(20.5)
                .name("Test AP")
                .build();
        
        when(accessPointRepository.findById(accessPointId)).thenReturn(Optional.of(expectedAccessPoint));

        // Act
        Optional<AccessPoint> result = accessPointService.getAccessPoint(accessPointId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(accessPointId, result.get().getId());
        verify(accessPointRepository).findById(accessPointId);
    }

    @Test
    void getAccessPoint_NonExistentId_ReturnsEmpty() {
        // Arrange
        when(accessPointRepository.findById(accessPointId)).thenReturn(Optional.empty());

        // Act
        Optional<AccessPoint> result = accessPointService.getAccessPoint(accessPointId);

        // Assert
        assertFalse(result.isPresent());
        verify(accessPointRepository).findById(accessPointId);
    }

    @Test
    void getAccessPointsByFloor_ExistingFloor_ReturnsAccessPoints() {
        // Arrange
        List<AccessPoint> expectedAccessPoints = List.of(
                AccessPoint.builder().id(UUID.randomUUID()).floor(testFloor).build(),
                AccessPoint.builder().id(UUID.randomUUID()).floor(testFloor).build()
        );
        
        when(accessPointRepository.findByFloorId(floorId)).thenReturn(expectedAccessPoints);

        // Act
        List<AccessPoint> result = accessPointService.getAccessPointsByFloor(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(accessPointRepository).findByFloorId(floorId);
    }

    @Test
    void updateAccessPoint_ExistingId_ReturnsUpdatedAccessPoint() {
        // Arrange
        String newMacAddress = "11:22:33:44:55:66";
        Double newX = 30.5;
        Double newY = 40.5;
        String newName = "Updated AP";
        
        AccessPoint existingAccessPoint = AccessPoint.builder()
                .id(accessPointId)
                .floor(testFloor)
                .macAddress("AA:BB:CC:DD:EE:FF")
                .coordinateX(10.5)
                .coordinateY(20.5)
                .name("Original AP")
                .build();
        
        AccessPoint updatedAccessPoint = AccessPoint.builder()
                .id(accessPointId)
                .floor(testFloor)
                .macAddress(newMacAddress)
                .coordinateX(newX)
                .coordinateY(newY)
                .name(newName)
                .build();
        
        when(accessPointRepository.findById(accessPointId)).thenReturn(Optional.of(existingAccessPoint));
        when(accessPointRepository.save(any(AccessPoint.class))).thenReturn(updatedAccessPoint);

        // Act
        AccessPoint result = accessPointService.updateAccessPoint(accessPointId, newMacAddress, newX, newY, newName);

        // Assert
        assertNotNull(result);
        assertEquals(accessPointId, result.getId());
        assertEquals(newMacAddress, result.getMacAddress());
        assertEquals(newX, result.getCoordinateX());
        assertEquals(newY, result.getCoordinateY());
        assertEquals(newName, result.getName());
        
        verify(accessPointRepository).findById(accessPointId);
        verify(accessPointRepository).save(any(AccessPoint.class));
    }

    @Test
    void updateAccessPoint_NonExistentId_ThrowsRuntimeException() {
        // Arrange
        when(accessPointRepository.findById(accessPointId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            accessPointService.updateAccessPoint(accessPointId, "AA:BB:CC:DD:EE:FF", 10.5, 20.5, "Test AP");
        });
        
        verify(accessPointRepository).findById(accessPointId);
        verify(accessPointRepository, never()).save(any());
    }
}
