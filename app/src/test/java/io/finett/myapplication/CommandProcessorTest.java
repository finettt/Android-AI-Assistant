package io.finett.myapplication;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.speech.tts.TextToSpeech;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.finett.myapplication.util.CommandProcessor;
import io.finett.myapplication.util.CommunicationManager;
import io.finett.myapplication.util.ContactsManager;
import io.finett.myapplication.util.BrowserHelper;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Activity.class, TextToSpeech.class})
public class CommandProcessorTest {

    @Mock
    private Activity activity;
    
    @Mock
    private TextToSpeech textToSpeech;
    
    @Mock
    private CommunicationManager communicationManager;
    
    @Mock
    private ContactsManager contactsManager;
    
    @Mock
    private BrowserHelper browserHelper;

    private CommandProcessor commandProcessor;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Activity.class);
        PowerMockito.mockStatic(TextToSpeech.class);
        when(activity.getApplicationContext()).thenReturn(activity);
        commandProcessor = new CommandProcessor(activity, textToSpeech, 
            communicationManager, contactsManager, browserHelper);
    }

    @Test
    public void testCallCommand() {
        // Arrange
        String command = "позвони ивану";
        ContactsManager.Contact contact = new ContactsManager.Contact("Иван", "+79001234567");
        when(contactsManager.findContactsByPartialName("ивану")).thenReturn(Collections.singletonList(contact));
        
        // Act
        boolean result = commandProcessor.processCommand(command);
        
        // Debug
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(contactsManager).findContactsByPartialName(nameCaptor.capture());
        System.out.println("Captured name: " + nameCaptor.getValue());
        
        // Assert
        assertTrue("Команда звонка должна быть распознана", result);
        verify(communicationManager).makePhoneCall("+79001234567");
        verify(textToSpeech).speak(eq("Набираю Иван"), eq(TextToSpeech.QUEUE_FLUSH), isNull(), isNull());
    }

    @Test
    public void testSmsCommand() {
        // Arrange
        String command = "отправь sms ивану с текстом привет";
        ContactsManager.Contact contact = new ContactsManager.Contact("Иван", "+79001234567");
        when(contactsManager.findContactsByPartialName("ивану")).thenReturn(Collections.singletonList(contact));
        
        // Act
        boolean result = commandProcessor.processCommand(command);
        
        // Debug
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(contactsManager).findContactsByPartialName(nameCaptor.capture());
        System.out.println("Captured name: " + nameCaptor.getValue());
        
        // Assert
        assertTrue("Команда отправки SMS должна быть распознана", result);
        verify(communicationManager).sendSms("+79001234567", "привет");
        verify(textToSpeech).speak(eq("Отправляю сообщение Иван"), eq(TextToSpeech.QUEUE_FLUSH), isNull(), isNull());
    }
} 