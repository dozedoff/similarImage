/*  Copyright (C) 2016  Nicholas Wright
    
    This file is part of similarImage - A similar image finder using pHash
    
    similarImage is free software: you can redistribute it and/or modify
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
package com.github.dozedoff.similarImage.app;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dozedoff.similarImage.db.FilterRecord;
import com.github.dozedoff.similarImage.db.ImageRecord;
import com.github.dozedoff.similarImage.db.Persistence;
import com.github.dozedoff.similarImage.db.Tag;
import com.github.dozedoff.similarImage.db.Thumbnail;
import com.github.dozedoff.similarImage.db.repository.FilterRepository;
import com.github.dozedoff.similarImage.db.repository.ImageRepository;
import com.github.dozedoff.similarImage.db.repository.RepositoryException;
import com.github.dozedoff.similarImage.db.repository.TagRepository;
import com.github.dozedoff.similarImage.db.repository.ormlite.OrmliteFilterRepository;
import com.github.dozedoff.similarImage.db.repository.ormlite.OrmliteImageRepository;
import com.github.dozedoff.similarImage.db.repository.ormlite.OrmliteTagRepository;
import com.github.dozedoff.similarImage.duplicate.DuplicateOperations;
import com.github.dozedoff.similarImage.gui.DisplayGroupView;
import com.github.dozedoff.similarImage.gui.SimilarImageController;
import com.github.dozedoff.similarImage.gui.SimilarImageView;
import com.github.dozedoff.similarImage.gui.UserTagSettingController;
import com.github.dozedoff.similarImage.io.Statistics;
import com.github.dozedoff.similarImage.thread.NamedThreadFactory;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;

public class SimilarImage {
	private final static Logger logger = LoggerFactory.getLogger(SimilarImage.class);

	private final String PROPERTIES_FILENAME = "similarImage.properties";
	private final int PRODUCER_QUEUE_SIZE = 400;

	private ExecutorService threadPool;
	private Statistics statistics;

	public static void main(String[] args) {
		try {
			new SimilarImage().init();
		} catch (SQLException | RepositoryException e) {
			logger.error("Startup failed: {}", e.toString());
			e.printStackTrace();
		}
	}

	public void init() throws SQLException, RepositoryException {
		String version = this.getClass().getPackage().getImplementationVersion();

		if (version == null) {
			version = "unknown";
		}

		logger.info("SimilarImage version " + version);
		logger.info("System has {} processors", Runtime.getRuntime().availableProcessors());

		threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new NamedThreadFactory(SimilarImage.class.getSimpleName()));
		Settings settings = new Settings(new SettingsValidator());
		settings.loadPropertiesFromFile(PROPERTIES_FILENAME);
		Persistence persistence = new Persistence();
		
		Dao<FilterRecord, Integer> filterDao = DaoManager.createDao(persistence.getCs(), FilterRecord.class);
		Dao<Tag, Long> tagDao = DaoManager.createDao(persistence.getCs(), Tag.class);
		Dao<Thumbnail, Integer> thumbnailDao = DaoManager.createDao(persistence.getCs(), Thumbnail.class);
		Dao<ImageRecord, String> imageDao = DaoManager.createDao(persistence.getCs(), ImageRecord.class);
		
		FilterRepository filterRepository = new OrmliteFilterRepository(filterDao, thumbnailDao);
		TagRepository tagRepository = new OrmliteTagRepository(tagDao);
		ImageRepository imageRepository = new OrmliteImageRepository(imageDao);
		
		statistics = new Statistics();
		DisplayGroupView dgv = new DisplayGroupView();
		SimilarImageController controller = new SimilarImageController(filterRepository, tagRepository, imageRepository,
				dgv, threadPool, statistics);
		DuplicateOperations dupOp = new DuplicateOperations(filterRepository, tagRepository, imageRepository);
		UserTagSettingController utsc = new UserTagSettingController(tagRepository);
		SimilarImageView gui = new SimilarImageView(controller, dupOp, PRODUCER_QUEUE_SIZE, utsc, filterRepository);

		controller.setGui(gui);

		logImageReaders();
	}

	private void logImageReaders() {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
		logger.debug("Loaded JPEG readers:");
		while (readers.hasNext()) {
			logger.debug("reader: " + readers.next());
		}
	}
}
