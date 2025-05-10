package io.finett.myapplication;

import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Locale;

import io.finett.myapplication.util.CommandProcessor;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class SystemAssistantCommandTest {

    private ActivityController<SystemAssistantActivity> activityController;
    private SystemAssistantActivity activity;

    @Mock
    private TextToSpeech textToSpeech;

    @Mock
    private CommandProcessor commandProcessor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Setup TextToSpeech mock
        when(textToSpeech.setLanguage(any(Locale.class))).thenReturn(TextToSpeech.SUCCESS);

        // Setup CommandProcessor mock
        when(commandProcessor.processCommand(anyString())).thenReturn(false);

        // Create and setup activity
        activityController = Robolectric.buildActivity(SystemAssistantActivity.class);
        activity = activityController.get();

        // Replace the real TTS with our mock
        activity.textToSpeech = textToSpeech;
        activity.commandProcessor = commandProcessor;

        activityController.create().start().resume();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    @Test
    public void testCommandProcessorInitialized() {
        assertNotNull("CommandProcessor should be initialized", activity.commandProcessor);
    }

    @Test
    public void testVoiceCommandProcessing() {
        // Simulate receiving a voice command that should be handled by CommandProcessor
        String testCommand = "включи вай-фай";
        
        // Mock CommandProcessor to return true (command was processed)
        when(commandProcessor.processCommand(testCommand)).thenReturn(true);
        
        // Create a mock intent with recognition results
        Intent data = new Intent();
        ArrayList<String> results = new ArrayList<>();
        results.add(testCommand);
        data.putExtra(RecognizerIntent.EXTRA_RESULTS, results);
        
        // Simulate activity result
        activity.onActivityResult(SystemAssistantActivity.REQUEST_SPEECH_RECOGNITION, 
                                 SystemAssistantActivity.RESULT_OK, data);
        
        // Verify CommandProcessor was called with the test command
        verify(commandProcessor).processCommand(testCommand);
        
        // Verify no API call was made (could be checked by adding a mock for the API service)
    }
    
    @Test
    public void testCommandProcessedCallback() {
        // Test that the CommandProcessor.OnCommandProcessedListener callback works
        
        // Create test command and response
        String testCommand = "какое сейчас время";
        String testResponse = "Сейчас 12:34";
        
        // Trigger the callback
        activity.onCommandProcessed(testCommand, testResponse);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Verify TTS was called with the response
        verify(textToSpeech).speak(
            Mockito.eq(testResponse), 
            Mockito.eq(TextToSpeech.QUEUE_FLUSH), 
            Mockito.isNull(), 
            Mockito.anyString()
        );
    }
    
    @Test
    public void testAssistantSpecificHelpCommand() {
        // Test help command
        String helpCommand = "что ты умеешь";
        
        // Create a mock intent with recognition results
        Intent data = new Intent();
        ArrayList<String> results = new ArrayList<>();
        results.add(helpCommand);
        data.putExtra(RecognizerIntent.EXTRA_RESULTS, results);
        
        // Simulate activity result
        activity.onActivityResult(SystemAssistantActivity.REQUEST_SPEECH_RECOGNITION, 
                                 SystemAssistantActivity.RESULT_OK, data);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Verify that TTS was called with help text
        verify(textToSpeech).speak(
            Mockito.contains("Я могу выполнять различные команды"), 
            Mockito.eq(TextToSpeech.QUEUE_FLUSH), 
            Mockito.isNull(), 
            Mockito.anyString()
        );
        
        // Verify that CommandProcessor was NOT called (since we handle this internally)
        verify(commandProcessor, Mockito.never()).processCommand(helpCommand);
    }
    
    @Test
    public void testAssistantSpecificExitCommand() {
        // Test exit command
        String exitCommand = "закрой";
        
        // Create a mock intent with recognition results
        Intent data = new Intent();
        ArrayList<String> results = new ArrayList<>();
        results.add(exitCommand);
        data.putExtra(RecognizerIntent.EXTRA_RESULTS, results);
        
        // Simulate activity result
        activity.onActivityResult(SystemAssistantActivity.REQUEST_SPEECH_RECOGNITION, 
                                 SystemAssistantActivity.RESULT_OK, data);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Verify that TTS was called with exit text
        verify(textToSpeech).speak(
            Mockito.eq("До свидания!"), 
            Mockito.eq(TextToSpeech.QUEUE_FLUSH), 
            Mockito.isNull(), 
            Mockito.anyString()
        );
        
        // Run delayed tasks (including the finish task)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks(2000);
        
        // Verify that activity is finishing
        // Note: This is hard to test in Robolectric, so we'd need to add some additional
        // instrumentation to make it testable (like a boolean flag in the activity)
    }
    
    @Test
    public void testCleanup() {
        // Test that resources are properly cleaned up
        activityController.pause().stop().destroy();
        
        // Verify TTS was shutdown
        verify(textToSpeech).stop();
        verify(textToSpeech).shutdown();
        
        // Verify CommandProcessor was cleaned up
        verify(commandProcessor).cleanup();
    }
} 