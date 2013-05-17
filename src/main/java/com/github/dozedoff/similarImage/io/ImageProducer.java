/*  Copyright (C) 2013  Nicholas Wright
    
    This file is part of similarImage - A similar image finder using pHash
    
    mmut is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.github.dozedoff.similarImage.io;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.JProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dozedoff.commonj.io.DataProducer;
import com.github.dozedoff.commonj.util.Pair;
import com.github.dozedoff.similarImage.db.Persistence;

public class ImageProducer extends DataProducer<Path, Pair<Path, BufferedImage>> {
	private static final Logger logger = LoggerFactory.getLogger(ImageProducer.class);
	private final JProgressBar bufferLevel, totalProgress;
	private final Persistence persistence = Persistence.getInstance();
	private final AtomicInteger total = new AtomicInteger();
	private final AtomicInteger processed = new AtomicInteger();
	
	public ImageProducer(int maxOutputQueueSize) {
		super(maxOutputQueueSize);
		totalProgress = new JProgressBar(processed.get(),total.get());
		totalProgress.setStringPainted(true);
		bufferLevel = new JProgressBar(0, maxOutputQueueSize);
		bufferLevel.setStringPainted(true);
	}
	
	public JProgressBar getBufferLevel() {
		return bufferLevel;
	}
	
	@Override
	public void addToLoad(List<Path> paths) {
		total.addAndGet(paths.size());
		super.addToLoad(paths);
	}
	
	@Override
	public void addToLoad(Path... paths) {
		total.addAndGet(paths.length);
		super.addToLoad(paths);
	}
	
	@Override
	public void clear() {
		super.clear();
		processed.set(0);
		total.set(0);
	}
	
	public JProgressBar getTotalProgress() {
		return totalProgress;
	}
	
	@Override
	protected void loaderDoWork() throws InterruptedException {
		try {
			Path next = input.take();
			if (persistence.isPathRecorded(next)) {
				processed.addAndGet(1);
				totalProgress.setValue(processed.get());
				return;
			}
			
			byte[] data = Files.readAllBytes(next);
			InputStream is = new ByteArrayInputStream(data);
			BufferedImage img = ImageIO.read(is);

			Pair<Path, BufferedImage> pair = new Pair<Path, BufferedImage>(next, img);
			output.put(pair);
		} catch (IOException e) {
			logger.warn("Failed to load file - {}", e.getMessage());
		} catch (SQLException e) {
			logger.warn("Failed to query database - {}", e.getMessage());
		} catch (Exception e) {
			logger.warn("Failed to process image - {}", e.getMessage());
		}
		
		processed.addAndGet(1);
		totalProgress.setValue(processed.get());
	}

	@Override
	protected void outputQueueChanged() {
		bufferLevel.setValue(output.size());
	}
}