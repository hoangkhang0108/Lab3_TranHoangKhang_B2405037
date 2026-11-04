package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private BookingService bookingService;

    @Test
    @DisplayName("Đặt phòng thành công")
    void testBookRoom_Success() {
        String roomId = "ROOM_101";
        int nights = 3;
        double pricePerNight = 100.0;
        double expectedTotalAmount = 300.0;
        String creditCard = "1234-5678-9012-3456";

        Room room = new Room(roomId, pricePerNight, true);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(paymentGateway.charge(creditCard, expectedTotalAmount)).thenReturn(true);

        BookingReceipt receipt = bookingService.bookRoom(roomId, nights, creditCard);

        assertNotNull(receipt);
        assertEquals(roomId, receipt.getRoomId());
        assertEquals(expectedTotalAmount, receipt.getTotalAmount(), 0.001);
        assertFalse(room.isAvailable());

        verify(roomRepository, times(1)).findById(roomId);
        verify(paymentGateway, times(1)).charge(creditCard, expectedTotalAmount);
        verify(roomRepository, times(1)).save(room);
    }

    @Test
    @DisplayName("Ném lỗi khi không tìm thấy phòng")
    void testBookRoom_RoomNotFound_ThrowsException() {
        String roomId = "ROOM_999";
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        assertThrows(RoomNotAvailableException.class,
                () -> bookingService.bookRoom(roomId, 2, "1234-5678-9012-3456"));

        verify(roomRepository, times(1)).findById(roomId);
        verifyNoInteractions(paymentGateway);
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném lỗi khi phòng đã được đặt")
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {
        String roomId = "ROOM_102";
        Room room = new Room(roomId, 150.0, false);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThrows(RoomNotAvailableException.class,
                () -> bookingService.bookRoom(roomId, 2, "1234-5678-9012-3456"));

        verify(roomRepository, times(1)).findById(roomId);
        verifyNoInteractions(paymentGateway);
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném lỗi khi thanh toán thất bại")
    void testBookRoom_PaymentFailed_ThrowsException() {
        String roomId = "ROOM_103";
        Room room = new Room(roomId, 200.0, true);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(paymentGateway.charge(anyString(), anyDouble())).thenReturn(false);

        assertThrows(PaymentFailedException.class,
                () -> bookingService.bookRoom(roomId, 2, "1234-5678-9012-3456"));

        assertTrue(room.isAvailable());
        verify(roomRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Ném lỗi khi số đêm ở <= 0")
    void testBookRoom_InvalidNights_ThrowsException(int invalidNights) {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookRoom("ROOM_101", invalidNights, "1234-5678-9012-3456"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Ném lỗi khi thẻ creditCard rỗng hoặc null")
    void testBookRoom_NullOrEmptyCreditCard_ThrowsException(String invalidCard) {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookRoom("ROOM_101", 2, invalidCard));
    }
}