package com.linkedpipes.etl.storage.template;

import com.linkedpipes.etl.storage.BaseException;
import org.openrdf.model.Statement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * @author Petr Škoda
 */
@Service
public class TemplateFacade {

    @Autowired
    private TemplateManager manager;

    /**
     * Return all templates that are ancestors to given template.
     *
     * @param templateIri
     * @param includeFirstLevelTemplates
     * @return
     */
    public Collection<Template> getTemplateHierarchy(String templateIri,
            boolean includeFirstLevelTemplates) {
        Template template = getTemplate(templateIri);
        if (template == null) {
            return Collections.EMPTY_LIST;
        }
        // Search for new.
        final Set<Template> templates = new HashSet<>();
        while (true) {
            if (template instanceof FullTemplate) {
                // Terminal template.
                if (includeFirstLevelTemplates) {
                    templates.add(template);
                }
                break;
            } else if (template instanceof ReferenceTemplate) {
                templates.add(template);
                //
                ReferenceTemplate ref = (ReferenceTemplate) template;
                template = getTemplate(ref.getTemplate());
                if (template == null) {
                    // Missing template.
                    // TODO Throw an exception ?
                    break;
                }
            }
        }
        return templates;
    }

    public Collection<Template> getTemplates() {
        return (Collection)manager.getTemplates().values();
    }

    public Template getTemplate(String iri) {
        return manager.getTemplates().get(iri);
    }

    public Collection<Statement> getInterface() {
        final Collection<Statement> result = new ArrayList<>();
        for (Template template : manager.getTemplates().values()) {
            result.addAll(((BaseTemplate) template).getInterfaceRdf());
        }
        return result;
    }

    public Collection<Statement> getInterface(Template template) {
        return ((BaseTemplate) template).getInterfaceRdf();
    }

    public Collection<Statement> getDefinition(Template template) {
        return ((BaseTemplate) template).getDefinitionRdf();
    }

    public Collection<Statement> getConfig(Template template) {
        return ((BaseTemplate) template).getConfigRdf();
    }

    public Collection<Statement> getConfigForInstance(Template template) {
        return ((BaseTemplate) template).getConfigForInstanceRdf();
    }

    public Collection<Statement> getConfigDesc(Template template) {
        return ((BaseTemplate) template).getConfigDescRdf();
    }

    public File getDialogResource(Template template, String dialog,
            String path) {
        if (template instanceof FullTemplate) {
            final FullTemplate fullTemplate = (FullTemplate) template;
            // TODO Check for potential security risk!
            return new File(fullTemplate.getDirectory(),
                    "/dialog/" + dialog + "/" + path);
        }
        return null;
    }

    public File getStaticResource(Template template, String path) {
        if (template instanceof FullTemplate) {
            final FullTemplate fullTemplate = (FullTemplate) template;
            // TODO Check for potential security risk!
            return new File(fullTemplate.getDirectory(), "/static/" + path);
        }
        return null;
    }

    /**
     * Create and return a template.
     *
     * @param template Instance to create template from.
     * @param configuration Configuration to used for template.
     * @return
     */
    public Template createTemplate(Collection<Statement> template,
            Collection<Statement> configuration) throws BaseException {
        return manager.createTemplate(template, configuration);
    }

}
