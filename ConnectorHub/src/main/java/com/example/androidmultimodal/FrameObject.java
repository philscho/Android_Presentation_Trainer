package com.example.androidmultimodal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FrameObject with a timestamp relative to the start of a recording and collection of data values
 *
 * Source code adapted from ConnectorHub library (C#) by Jan Schneider
 *
 * @author Philipp Scholl
 * @since 17.05.2019
 *
 */
public class FrameObject {

    public String frameStamp;
    public Map<String, String> frameAttributes;

    /**
     *
     *
     * @param start             Start of recording
     * @param attributeNames    Names of attributes
     * @param attributeValues   Values for attributes
     * @param timespan          SimpleDateFormat to create timestamp
     */
    public FrameObject(long start, List<String> attributeNames, List<String> attributeValues, SimpleDateFormat timespan) {
        long now = System.currentTimeMillis();

        // Calculates time relative to start of the recording and formats it for use by LearningHub
        this.frameStamp = timespan.format(new Date(now - start));
        frameAttributes = new HashMap<>();
        for(int i=0; i < attributeNames.size(); i++)
        {
            frameAttributes.put(attributeNames.get(i), attributeValues.get(i));
        }
    }

    public FrameObject() {}

}
