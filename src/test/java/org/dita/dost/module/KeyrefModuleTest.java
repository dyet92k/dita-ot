/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2016 Jarno Elovirta
 *
 *  See the accompanying LICENSE file for applicable license.
 */

package org.dita.dost.module;

import static java.net.URI.create;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.dita.dost.TestUtils.assertXMLEqual;
import static org.dita.dost.TestUtils.createTempDir;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.dita.dost.TestUtils;
import org.dita.dost.TestUtils.TestLogger;
import org.dita.dost.module.KeyrefModule.ResolveTask;
import org.dita.dost.store.StreamStore;
import org.dita.dost.util.Job;
import org.dita.dost.util.Job.FileInfo.Builder;
import org.dita.dost.util.KeyDef;
import org.dita.dost.util.KeyScope;
import org.dita.dost.util.XMLUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.common.collect.ImmutableMap;

public class KeyrefModuleTest {

    private static final File baseDir = TestUtils.getResourceDir(KeyrefModuleTest.class);
    private static final URI inputMap = new File(baseDir, "xsrc" + File.separator + "test.ditamap").toURI();
    private static final URI subMap = new File(baseDir, "xsrc" + File.separator + "submap.ditamap").toURI();

    private File tempDir;
    private KeyrefModule module;

    @Before
    public void setUp() throws IOException {
        tempDir = createTempDir(KeyrefModuleTest.class);

        module = new KeyrefModule();
        final Job job = new Job(tempDir, new StreamStore(tempDir, new XMLUtils()));
        job.setInputDir(new File(baseDir, "xsrc").toURI());
        job.setInputMap(URI.create("test.ditamap"));
        job.add(new Job.FileInfo.Builder()
                .uri(URI.create("submap.ditamap"))
                .src(new File(baseDir, "xsrc" + File.separator + "submap.ditamap").toURI())
                .result(new File(baseDir, "xsrc" + File.separator + "submap.ditamap").toURI())
                .format("ditamap")
                .hasKeyref(true)
                .build());
        job.add(new Job.FileInfo.Builder()
                .uri(URI.create("topic.dita"))
                .src(new File(baseDir, "xsrc" + File.separator + "topic.dita").toURI())
                .result(new File(baseDir, "xsrc" + File.separator + "topic.dita").toURI())
                .format("dita")
                .hasKeyref(true)
                .build());
        job.add(new Job.FileInfo.Builder()
                .uri(URI.create("test.ditamap"))
                .src(new File(baseDir, "xsrc" + File.separator + "test.ditamap").toURI())
                .result(new File(baseDir, "xsrc" + File.separator + "test.ditamap").toURI())
                .format("ditamap")
                .isInput(true)
                .build());
        module.setJob(job);
        module.setLogger(new TestLogger());
    }

    @After
    public void tearDown() throws IOException {
       TestUtils.forceDelete(tempDir);
    }

    @Test
    public void testAdjustResourceRenames() {
        final KeyScope scope = new KeyScope("scope", "scope",
                ImmutableMap.<String, KeyDef>builder()
                        .put("key", new KeyDef("key", create("target.dita"), null, null, null, null))
                        .build(),
                emptyList());
        final List<ResolveTask> src = singletonList(
                new ResolveTask(
                        scope,
                        new Builder().uri(create("target.dita")).build(),
                        new Builder().uri(create("target-1.dita")).build()));
        final List<ResolveTask> act = module.adjustResourceRenames(src);

        final KeyScope exp = new KeyScope("scope", "scope",
                ImmutableMap.<String, KeyDef>builder()
                        .put("key", new KeyDef("key", create("target-1.dita"), null, null, null, null))
                        .build(),
                emptyList());

        assertEquals(exp, act.get(0).scope);
    }

    @Test
    public void testRewriteScopeTargets() {
        final KeyScope src = new KeyScope("scope", "scope",
                ImmutableMap.<String, KeyDef>builder()
                        .put("key", new KeyDef("key", create("target.dita"), null, null, null, null))
                        .put("element", new KeyDef("element", create("target.dita#target/element"), null, null, null, null))
                        .build(),
                emptyList());
        final Map<URI, URI> rewrites = ImmutableMap.<URI, URI>builder()
                .put(create("target.dita"), create("target-1.dita"))
                .build();
        final KeyScope act = module.rewriteScopeTargets(src, rewrites);

        final KeyScope exp = new KeyScope("scope", "scope",
                ImmutableMap.<String, KeyDef>builder()
                        .put("key", new KeyDef("key", create("target-1.dita"), null, null, null, null))
                        .put("element", new KeyDef("element", create("target-1.dita#target/element"), null, null, null, null))
                        .build(),
                emptyList());

        assertEquals(exp, act);
    }

    @Test
    public void testWalkMap() throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        final Document act = b.parse(new File(baseDir, "src" + File.separator + "test.ditamap"));
        final KeyScope childScope = new KeyScope("A", "A",
                            ImmutableMap.of(
                                    "VAR", new KeyDef("VAR", null, "local", "dita", inputMap, null),
                                    "A.VAR", new KeyDef("VAR", null, "local", "dita", inputMap, null)
                            ),
                            EMPTY_LIST
        );
        final KeyScope keyScope =
                new KeyScope("#root", null,
                            ImmutableMap.of(
                                    "VAR", new KeyDef("VAR", null, "local", "dita", inputMap, null),
                                    "A.VAR", new KeyDef("VAR", null, "local", "dita", inputMap, null)
                            ),
                            singletonList(childScope)
        );
        final List<ResolveTask> res = new ArrayList<>();
        module.walkMap(act.getDocumentElement(), keyScope, res);
        final Document exp = b.parse(new File(baseDir, "exp" + File.separator + "test.ditamap"));

        final ResolveTask subMapTask = res.stream().filter(r -> r.in.src.equals(subMap)).findFirst().get();
        assertEquals(subMapTask.scope, childScope);

        assertXMLEqual(exp, act);
    }

	@Test
	public void testWalkMapAndRewriteKeydefHref() throws ParserConfigurationException, IOException, SAXException, URISyntaxException {
	    final DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	    File mapFile = new File(baseDir, "src" + File.separator + "test2.ditamap");
	    URI inputMap = mapFile.toURI();
	    final Document act = b.parse(mapFile);
	    final KeyScope childScope1 = new KeyScope("A", "A",
	    		ImmutableMap.of(
	    				"VAR", new KeyDef("VAR", new URI("topic.dita"), "local", "dita", inputMap, null),
	    				"A.VAR", new KeyDef("A.VAR", new URI("topic.dita"), "local", "dita", inputMap, null)
	    				),
	    		EMPTY_LIST
	    		);
	    final KeyScope childScope2 = new KeyScope("B", "B",
	    		ImmutableMap.of(
	    				"VAR", new KeyDef("VAR", new URI("topic.dita"), "local", "dita", inputMap, null),
	    				"B.VAR", new KeyDef("B.VAR", new URI("topic.dita"), "local", "dita", inputMap, null)
	    				),
	    		EMPTY_LIST
	    		);
	    final KeyScope keyScope =
	            new KeyScope("#root", null, new HashMap<String, KeyDef>(), 
	                        Arrays.asList(new KeyScope[] {childScope1, childScope2})
	    );
	    final List<ResolveTask> res = new ArrayList<>();
	    module.walkMap(act.getDocumentElement(), keyScope, res);

	    ResolveTask task = res.get(0);
	    assertEquals("topic.dita", task.in.file.toString());
	    assertEquals(null, task.scope.name);
	    
	    task = res.get(1);
	    assertEquals("topic.dita", task.in.file.toString());
	    assertEquals("A", task.scope.name);
	    KeyDef keyDef = task.scope.keyDefinition.get("VAR");
	    assertEquals(new URI("topic.dita"), keyDef.href);
	    keyDef = task.scope.keyDefinition.get("A.VAR");
	    assertEquals(new URI("topic-1.dita"), keyDef.href);
	    
	    task = res.get(2);
	    assertEquals("topic.dita", task.in.file.toString());
	    assertEquals("B", task.scope.name);
	    keyDef = task.scope.keyDefinition.get("VAR");
	    assertEquals(new URI("topic.dita"), keyDef.href);
	    keyDef = task.scope.keyDefinition.get("B.VAR");
	    assertEquals(new URI("topic-2.dita"), keyDef.href);
	}
}