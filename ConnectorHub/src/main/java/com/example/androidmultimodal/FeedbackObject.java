package com.example.androidmultimodal;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * FeedbackObject containing feedback with information about application and recording
 *
 * Source code adapted from ConnectorHub library (C#) by Jan Schneider
 *
 * @author Philipp Scholl
 * @since 17.05.2019
 *
 */
public class FeedbackObject {

    public String frameStamp;
    public String applicationName;
    public String verb;

    /**
     *
     * @param start             Start of recording
     * @param feedbackValue     Feedback to be sent
     * @param applicationName   Application that created feedback
     * @param timespan          SimpleDateFormat to create timestamp
     */
    public FeedbackObject(long start, String feedbackValue, String applicationName, SimpleDateFormat timespan) {
        long now = System.currentTimeMillis();

        this.frameStamp = timespan.format(new Date(now - start));
        this.applicationName = applicationName;
        this.verb = feedbackValue;
    }

}
