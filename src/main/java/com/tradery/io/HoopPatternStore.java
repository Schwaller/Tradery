package com.tradery.io;

import com.tradery.model.HoopPattern;

import java.io.File;

/**
 * Reads and writes HoopPattern JSON files.
 * Each pattern has its own folder: ~/.tradery/hoops/{id}/hoop.json
 *
 * Claude Code can directly read/write these files.
 */
public class HoopPatternStore extends JsonStore<HoopPattern> {

    public HoopPatternStore(File directory) {
        super(directory);
    }

    @Override
    protected String getFileName() {
        return "hoop.json";
    }

    @Override
    protected Class<HoopPattern> getEntityClass() {
        return HoopPattern.class;
    }

    @Override
    protected String getEntityName() {
        return "hoop pattern";
    }
}
