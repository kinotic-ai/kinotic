package org.kinotic.sql.parsers;

import java.util.ArrayList;
import java.util.List;

import org.kinotic.sql.domain.statements.ColumnTemplatePart;
import org.kinotic.sql.domain.statements.SettingTemplatePart;
import org.kinotic.sql.domain.statements.TemplatePart;
import org.kinotic.sql.parser.KinoticSQLParser;

/**
 * Helper class for parsing template parts from SQL statements.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class TemplatePartParser {
    
    public static List<TemplatePart> parseTemplateParts(List<KinoticSQLParser.TemplatePartContext> parts) {
        var result = new ArrayList<TemplatePart>();
        
        for (var part : parts) {
            if (part.NUMBER_OF_SHARDS() != null) {
                result.add(new SettingTemplatePart(
                    "NUMBER_OF_SHARDS",
                    part.INTEGER_LITERAL().getText()
                ));
            } else if (part.NUMBER_OF_REPLICAS() != null) {
                result.add(new SettingTemplatePart(
                    "NUMBER_OF_REPLICAS",
                    part.INTEGER_LITERAL().getText()
                ));
            } else if (part.columnDefinition() != null) {
                var name = part.columnDefinition().ID().getText();
                result.add(new ColumnTemplatePart(TypeParser.parseColumnType(name, part.columnDefinition().type())));
            }
        }
        
        return result;
    }
} 