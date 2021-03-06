package com.linkedpipes.etl.component.api.impl;

import com.linkedpipes.etl.component.api.impl.rdf.RdfReader;
import com.linkedpipes.etl.executor.api.v1.RdfException;
import com.linkedpipes.etl.executor.api.v1.rdf.SparqlSelect;

import java.util.HashMap;
import java.util.Map;

/**
 * Can be used to merge configurations.
 *
 * @author Petr Škoda
 */
class ConfigurationController implements RdfReader.MergeOptionsFactory {

    public class MergerPolicy implements RdfReader.MergeOptions {

        private final Map<String, String> options;

        private MergerPolicy(Map<String, String> options) {
            this.options = options;
        }

        @Override
        public boolean load(String predicate) {
            final String control = options.get(predicate);
            if (control == null) {
                return true;
            }
            switch (control) {
                case "http://plugins.linkedpipes.com/resource/configuration/Inherit":
                case "http://plugins.linkedpipes.com/resource/configuration/Forced":
                    return false;
                default:
                    // Load by default.
                    return true;
            }
        }
    }

    /**
     * Object with pipeline and configurations definition.
     */
    private final SparqlSelect definition;

    public ConfigurationController(SparqlSelect definition) {
        this.definition = definition;
    }

    @Override
    public RdfReader.MergeOptions create(String resourceIri, String graph)
            throws RdfException {
        final Map<String, String> options = new HashMap<>();
        // In case of duplicity we just end up with one of the values,
        // that should really not be an issue. As all the configuration
        // description classes should be the same, only with different
        // resource IRIs.
        for (Map<String, String> record
                : definition.executeSelect(getQuery(resourceIri, graph))) {
            options.put(record.get("property"), record.get("controlValue"));
        }
        return new MergerPolicy(options);
    }

    /**
     * The output values are "property" and "controlValue".
     *
     * @param resourceIri
     * @param graph
     * @return
     */
    protected static String getQuery(String resourceIri, String graph) {
        return "PREFIX config: <http://plugins.linkedpipes.com/ontology/configuration/>\n" +
                "\n" +
                "SELECT ?property ?controlValue WHERE {\n" +
                " GRAPH <" + graph + "> {\n" +
                "  <" + resourceIri + "> a ?type ;\n" +
                "    ?property [] ;\n" +
                "    ?control ?controlValue .\n" +
                " }\n" +
                "\n" +
                "GRAPH ?g {" +
                "  [] ?a <http://plugins.linkedpipes.com/ontology/ConfigurationDescription> ;\n" +
                "   config:type ?type ;\n" +
                "   config:member ?member.\n" +
                "\n" +
                "  ?member a <http://plugins.linkedpipes.com/ontology/configuration/ConfigurationMember> ;\n" +
                "    config:property ?property ;\n" +
                "    config:control ?control .\n" +
                " }\n" +
                "}";
    }

}
