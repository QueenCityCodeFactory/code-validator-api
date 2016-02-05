package org.sitenv.vocabularies.engine;

import com.orientechnologies.orient.object.db.OObjectDatabaseTx;
import org.apache.log4j.Logger;
import org.sitenv.vocabularies.constants.VocabularyConstants;
import org.sitenv.vocabularies.data.CodeSystemResult;
import org.sitenv.vocabularies.data.CodeValidationResult;
import org.sitenv.vocabularies.data.ValueSetValidationResult;
import org.sitenv.vocabularies.loader.code.CodeLoader;
import org.sitenv.vocabularies.loader.code.CodeLoaderManager;
import org.sitenv.vocabularies.loader.valueset.ValueSetLoader;
import org.sitenv.vocabularies.loader.valueset.ValueSetLoaderManager;
import org.sitenv.vocabularies.model.CodeModel;
import org.sitenv.vocabularies.model.ValueSetModel;
import org.sitenv.vocabularies.model.VocabularyModelDefinition;
import org.sitenv.vocabularies.repository.VocabularyRepository;
import org.sitenv.vocabularies.watchdog.RepositoryWatchdog;

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class ValidationEngine {
	
	private static Logger logger = Logger.getLogger(ValidationEngine.class);
	private static RepositoryWatchdog codeWatchdog = null;
	private static RepositoryWatchdog valueSetWatchdog = null;
	
	public static RepositoryWatchdog getCodeWatchdogThread() {
		return codeWatchdog;
	}
	
	public static RepositoryWatchdog getValueSetWatchdogThread() {
		return valueSetWatchdog;
	}
	
	public static boolean isCodeSystemLoaded(String codeSystem) {
		VocabularyModelDefinition vocabulary = null;
		VocabularyRepository ds = VocabularyRepository.getInstance();
		if (codeSystem != null) {
			Map<String, VocabularyModelDefinition> vocabMap = ds.getVocabularyMap();
			
			if (vocabMap != null) {
				vocabulary = vocabMap.get(codeSystem);
			}
		}
		return (vocabulary != null);
	}
	
	public static boolean isValueSetLoaded(String valueSet) {
		VocabularyRepository ds = VocabularyRepository.getInstance();
		Boolean model = false;
		if (valueSet != null  &&  ds != null && ds.getValueSetModelClassList() != null) {
			OObjectDatabaseTx dbConnection = ds.getActiveDbConnection();
			for (Class<? extends ValueSetModel> clazz : ds.getValueSetModelClassList()) {
				model = ds.valueSetExists(clazz, valueSet, dbConnection);
				if (model != null && model) {
					model = true;
				}
			}
			dbConnection.close();
		}
		return model;
	}
	
	public static CodeValidationResult validateCode(String codeSystem, String codeSystemName, String code, String displayName) {
		VocabularyRepository ds = VocabularyRepository.getInstance();
		CodeValidationResult result = new CodeValidationResult();
		OObjectDatabaseTx dbConnection = ds.getActiveDbConnection();
		result.setRequestedCode(code);
		result.setRequestedCodeSystemName(codeSystemName);
		result.setRequestedCodeSystemOid(codeSystem);
		result.setRequestedDisplayName(displayName);

		if (codeSystem == null) {
			codeSystem = VocabularyConstants.CODE_SYSTEM_MAP.get(codeSystemName);
			result.getExpectedOidsForCodeSystemName().add(codeSystem);
		} else {
			if (codeSystemName != null) {
				if (codeSystem.equalsIgnoreCase(VocabularyConstants.CODE_SYSTEM_MAP.get(codeSystemName))) {
					result.setCodeSystemAndNameMatch(true);
					result.getExpectedOidsForCodeSystemName().add(codeSystem);
					
				}
				for (String codeSystemNameLkp : VocabularyConstants.CODE_SYSTEM_MAP.keySet()) {
					if (VocabularyConstants.CODE_SYSTEM_MAP.get(codeSystemNameLkp).equalsIgnoreCase(codeSystem)) {
						result.getExpectedCodeSystemNamesForOid().add(codeSystemNameLkp);
					}
				}
			}
		}

		List<? extends CodeModel> codeModels = getCode(codeSystem, code, dbConnection, ds);
		if (codeModels != null && codeModels.size() > 0) {
			result.setCodeExistsInCodeSystem(true);
			for (CodeModel model : codeModels) {
				result.getExpectedDisplayNamesForCode().add(model.getDisplayName());
				// case sensitive compare of displayName
				if (displayName != null && model.getDisplayName() != null && model.getDisplayName().equals(displayName)) {
					result.setDisplayNameExistsForCode(true);
				}
			}
		}

		List<? extends CodeModel> displayNameModels = getCode(codeSystem, code, dbConnection, ds);
		if (displayNameModels != null && displayNameModels.size() > 0) {
			result.setDisplayNameExistsInCodeSystem(true);
			for (CodeModel model : displayNameModels) {
				result.getExpectedCodesForDisplayName().add(model.getCode());
			}
		}
		dbConnection.close();
		return result;
	}
	
	public static ValueSetValidationResult validateValueSetCode (String valueSet, String codeSystem, String codeSystemName, String code, String description) {
		VocabularyRepository ds = VocabularyRepository.getInstance();
		OObjectDatabaseTx dbConnection = ds.getActiveDbConnection();
		ValueSetValidationResult result = new ValueSetValidationResult();
		result.setRequestedCode(code);
		result.setRequestedCodeSystemName(codeSystemName);
		result.setRequestedCodeSystemOid(codeSystem);
		result.setRequestedDescription(description);
		result.setRequestedValueSetOid(valueSet);
		
		Set<String> valueSetNames = getValueSetNames(valueSet, dbConnection, ds);
		result.getValueSetNames().addAll(valueSetNames);

		List<? extends ValueSetModel> codeModels = getValueSetCode(valueSet, code, dbConnection, ds);

		if (codeModels != null && codeModels.size() > 0) {
			result.setCodeExistsInValueSet(true);
			for (ValueSetModel model : codeModels) {
				result.getExpectedDescriptionsForCode().add(model.getDescription());
				result.getExpectedCodeSystemsForCode().add(model.getCodeSystem());
				
				// case sensitive compare of displayName
				if (description != null && model.getDescription() != null && model.getDescription().equals(description)) {
					result.setDescriptionMatchesCode(true);
				}
				
				if (codeSystem != null && model.getCodeSystem() != null && model.getCodeSystem().equals(codeSystem)) {
					result.setCodeExistsInCodeSystem(true);
				}
			}
		}
		
		List<? extends ValueSetModel> descriptionModels = getValueSetDescription(valueSet, description, dbConnection, ds);
		
		if (descriptionModels != null && descriptionModels.size() > 0) {
			result.setDescriptionExistsInValueSet(true);
			for (ValueSetModel model : descriptionModels) {
				result.getExpectedCodesForDescription().add(model.getCode());
				if (codeSystem != null && model.getCodeSystem() != null && model.getCodeSystem().equalsIgnoreCase(codeSystem)) {
					result.setDescriptionExistsInCodeSystem(true);
				}
			}
		}
		
		List<CodeSystemResult> codeSystemModels  = getValueSetCodeSystems(valueSet, dbConnection, ds);
		
		if (codeSystemModels != null && codeSystemModels.size() > 0) {
			for (CodeSystemResult system : codeSystemModels) {
				result.getExpectedCodeSystemsForValueSet().add(system.getCodeSystem());
				
				if (codeSystem != null && system.getCodeSystem() != null && system.getCodeSystem().equalsIgnoreCase(codeSystem)) {
					result.setCodeSystemExistsInValueSet(true);
					result.getExpectedCodeSystemNamesForOid().add(system.getCodeSystemName());
				}
				
				if (codeSystemName != null && system.getCodeSystemName() != null && system.getCodeSystemName().equalsIgnoreCase(codeSystemName)) {
					
					result.getExpectedOidsForCodeSystemName().add(system.getCodeSystem());
					if (codeSystem != null && system.getCodeSystem() != null && system.getCodeSystem().equalsIgnoreCase(codeSystem)) {
						result.setCodeSystemAndNameMatch(true);
					}
					
				}
			}
		}
		dbConnection.close();
		return result;
	}
	
	private static List<ValueSetModel> getValueSetCode(String valueSet, String code, OObjectDatabaseTx dbConnection, VocabularyRepository ds) {
		List<ValueSetModel> result = null;
		if (valueSet != null && code != null &&  ds != null && ds.getValueSetModelClassList() != null) {
			for (Class<? extends ValueSetModel> clazz : ds.getValueSetModelClassList()) {
				List<? extends ValueSetModel> modelList = ds.fetchByValueSetAndCode(clazz, valueSet, code, dbConnection);
				if (modelList != null) {
					if (result == null) {
						result = new ArrayList<ValueSetModel>();
					}
					result.addAll(modelList);
				}
			}
		}
		return result;
	}
	
	private static Set<String> getValueSetNames(String valueSet, OObjectDatabaseTx dbConnection, VocabularyRepository ds) {
		Set<String> result = null;
		if (valueSet != null &&  ds != null && ds.getValueSetModelClassList() != null) {
			for (Class<? extends ValueSetModel> clazz : ds.getValueSetModelClassList()) {
				Set<String> modelList = ds.fetchValueSetNamesByValueSet(clazz, valueSet, dbConnection);
				if (modelList != null) {
					if (result == null) {
						result = new TreeSet<String>();
					}
					result.addAll(modelList);
				}
			}
		}
		return result;
	}
	
	private static List<ValueSetModel> getValueSetDescription(String valueSet, String description, OObjectDatabaseTx dbConnection, VocabularyRepository ds) {
		List<ValueSetModel> result = null;
		if (valueSet != null && description != null &&  ds != null && ds.getValueSetModelClassList() != null) {
			for (Class<? extends ValueSetModel> clazz : ds.getValueSetModelClassList()) {
				List<? extends ValueSetModel> modelList = ds.fetchByValueSetAndDescription(clazz, valueSet, description, dbConnection);
				if (modelList != null) {
					if (result == null) {
						result = new ArrayList<ValueSetModel>();
					}
					result.addAll(modelList);
				}
			}
		}
		return result;
	}
	
	private static List<CodeSystemResult> getValueSetCodeSystems(String valueSet, OObjectDatabaseTx dbConnection, VocabularyRepository ds) {
		List<CodeSystemResult> result = null;
		if (valueSet != null &&  ds != null && ds.getValueSetModelClassList() != null) {
			for (Class<? extends ValueSetModel> clazz : ds.getValueSetModelClassList()) {
				List<CodeSystemResult> modelList = ds.fetchCodeSystemsByValueSet(clazz, valueSet, dbConnection);
				if (modelList != null) {
					if (result == null) {
						result = new ArrayList<CodeSystemResult>();
					}
					result.addAll(modelList);
				}
			}
		}
		return result;
	}
	
	private static List<? extends CodeModel> getCode(String codeSystem, String code, OObjectDatabaseTx dbConnection, VocabularyRepository ds) {
		List<? extends CodeModel> results = null;
		if (codeSystem != null && code != null &&  ds != null && ds.getVocabularyMap() != null) {
			Map<String, VocabularyModelDefinition> vocabMap = ds.getVocabularyMap();
			VocabularyModelDefinition vocab = vocabMap.get(codeSystem);
			results = ds.fetchByCode(vocab.getClazz(), code, dbConnection);
		}
		return results;
	}


	private static List<? extends CodeModel> getDisplayName(String codeSystem, String displayName, OObjectDatabaseTx dbConnection, VocabularyRepository ds) {
		List<? extends CodeModel> results = null;
		if (codeSystem != null && displayName != null &&  ds != null && ds.getVocabularyMap() != null) {
			Map<String, VocabularyModelDefinition> vocabMap = ds.getVocabularyMap();
			VocabularyModelDefinition vocab = vocabMap.get(codeSystem);
			results = ds.fetchByDisplayName(vocab.getClazz(), displayName, dbConnection);
		}
		return results;
	}
	
	public static synchronized void initialize(String codeDirectory, String valueSetDirectory, boolean loadAtStartup) throws IOException {
		boolean recursive = true;
		logger.info("Registering Loaders...");
		// register Loaders
		registerLoaders();
		logger.info("Loaders Registered...");
		// Validation Engine should load using the primary database (existing). This will kick off the loading of the secondary database and swap configs
		// Once the secondary dB is loaded, the watchdog thread will be initialized to monitor future changes.
		// Putting this initialization code in a separate thread will dramatically speed up the tomcat launch time
		InitializerThread initializer = new InitializerThread();
		initializer.setCodeDirectory(codeDirectory);
		initializer.setValueSetDirectory(valueSetDirectory);
		initializer.setRecursive(recursive);
		initializer.setLoadAtStartup(loadAtStartup);
		initializer.start();
	}
	
	public static void registerLoaders() {
		try {
			Class.forName("org.sitenv.vocabularies.loader.code.snomed.SnomedLoader");
			Class.forName("org.sitenv.vocabularies.loader.code.loinc.LoincLoader");
			Class.forName("org.sitenv.vocabularies.loader.code.rxnorm.RxNormLoader");
			Class.forName("org.sitenv.vocabularies.loader.code.icd9.Icd9CmDxLoader");
			Class.forName("org.sitenv.vocabularies.loader.code.icd9.Icd9CmSgLoader");
			Class.forName("org.sitenv.vocabularies.loader.code.icd10.Icd10CmLoader");
			Class.forName("org.sitenv.vocabularies.loader.code.icd10.Icd10PcsLoader");
			Class.forName("org.sitenv.vocabularies.loader.valueset.vsac.VsacLoader");
		} catch (ClassNotFoundException e) {
			// TODO: log4j
			logger.error("Error Initializing Loaders", e);
		}
	}
	
	public static void loadValueSetDirectory(String directory) throws IOException {
		File dir = new File(directory);
		if (dir.isFile()) {
			logger.debug("Directory to Load is a file and not a directory");
			throw new IOException("Directory to Load is a file and not a directory");
		} else {
			File[] list = dir.listFiles();
			for (File file : list) {
				loadValueSetFiles(file);
			}
		}
	}
	
	private static void loadCodeFiles(File directory) throws IOException {
		if (directory.isDirectory() && !directory.isHidden()) {
			File[] filesToLoad = directory.listFiles();
			String codeSystem = null;
			logger.debug("Building Loader for directory: " + directory.getName() + "...");
			CodeLoader loader = CodeLoaderManager.getInstance().buildLoader(directory.getName());
			if (loader != null && filesToLoad != null) {
				logger.debug("Loader built...");
				codeSystem = loader.getCodeSystem();
				//logger.debug("Loading file: " + loadFile.getAbsolutePath() + "...");
				loader.load(Arrays.asList(filesToLoad));
				logger.debug("File loaded...");
			} else {
				logger.debug("Building of Loader Failed.");
			}
		}
	}

	public static void loadCodeDirectory(String directory) throws IOException {
		File dir = new File(directory);
		if (dir.isFile()) {
			logger.debug("Directory to Load is a file and not a directory");
			throw new IOException("Directory to Load is a file and not a directory");
		} else {
			File[] list = dir.listFiles();
			for (File file : list) {
				loadCodeFiles(file);
			}
		}
	}
	
	private static void loadValueSetFiles(File directory) throws IOException {
		if (directory.isDirectory() && !directory.isHidden()) {
			File[] filesToLoad = directory.listFiles();
			String valueSet = null;
			
			logger.debug("Building Loader for directory: " + directory.getName() + "...");
			ValueSetLoader loader = ValueSetLoaderManager.getInstance().buildLoader(directory.getName());
			if (loader != null && filesToLoad != null) {
				logger.debug("Loader built...");
				valueSet = loader.getValueSetAuthorName();
				//logger.debug("Loading file: " + loadFile.getAbsolutePath() + "...");
				loader.load(Arrays.asList(filesToLoad));
				logger.debug("File loaded...");
			} else {
				logger.debug("Building of Loader Failed.");
			}
		}
	}
	
	private static class InitializerThread extends Thread {
		private String codeDirectory = null;
		private String valueSetDirectory = null;
		private boolean recursive = true;
		private boolean loadAtStartup = false;

		public String getCodeDirectory() {
			return codeDirectory;
		}

		public void setCodeDirectory(String codeDirectory) {
			this.codeDirectory = codeDirectory;
		}

		public String getValueSetDirectory() {
			return valueSetDirectory;
		}

		public void setValueSetDirectory(String valueSetDirectory) {
			this.valueSetDirectory = valueSetDirectory;
		}

		public boolean isRecursive() {
			return recursive;
		}

		public void setRecursive(boolean recursive) {
			this.recursive = recursive;
		}

		public boolean isLoadAtStartup() {
			return loadAtStartup;
		}

		public void setLoadAtStartup(boolean loadAtStartup) {
			this.loadAtStartup = loadAtStartup;
		}

		public void run() {
			try {
				if (loadAtStartup) {
					if (codeDirectory != null && !codeDirectory.trim().equals("")) {
						logger.info("Loading vocabularies at: " + codeDirectory + "...");
						loadCodeDirectory(codeDirectory);
						logger.info("Vocabularies loaded...");
					}
					
					if (valueSetDirectory != null && !valueSetDirectory.trim().equals("")) {
						logger.info("Loading value sets at: " + valueSetDirectory + "...");
						loadValueSetDirectory(valueSetDirectory);
						logger.info("Value Sets loaded...");
					}	
						
					logger.info("Activating new Vocabularies Map...");
					VocabularyRepository.getInstance().toggleActiveDatabase();
					logger.info("New vocabulary Map Activated...");
					
					if (codeDirectory != null && !codeDirectory.trim().equals("")) {
						logger.info("Loading vocabularies to new inactive repository at: " + codeDirectory + "...");
						loadCodeDirectory(codeDirectory);
						logger.info("Vocabularies loaded...");
					}
					
					if (valueSetDirectory != null && !valueSetDirectory.trim().equals("")) {
						logger.info("Loading value sets to new inactive repository at: " + valueSetDirectory + "...");
						loadValueSetDirectory(valueSetDirectory);
						logger.info("Value Sets loaded...");
					}
				}
				// recommendation from cwatson: load files back in the primary so both db's are
				logger.info("Starting Vocabulary Watchdog...");
				ValidationEngine.codeWatchdog = new RepositoryWatchdog(this.getCodeDirectory(), this.isRecursive(), false);
				ValidationEngine.codeWatchdog.start();
				logger.info("Vocabulary Watchdog started...");
				
				logger.info("Starting Value Set Watchdog...");
				ValidationEngine.valueSetWatchdog = new RepositoryWatchdog(this.getValueSetDirectory(), this.isRecursive(), false);
				ValidationEngine.valueSetWatchdog.start();
				logger.info("Vocabulary ValueSet started...");
			} catch (Exception e) {
				logger.error("Failed to load configured vocabulary directory.", e);
			}
			// TODO: Perform Validation/Verification, if needed
			Runtime.getRuntime().gc();
		}
	}
}
