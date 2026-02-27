package org.kinotic.test.tests.core.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.sample.Person;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 5/12/23.
 */
@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class StructureAndPersonHolder {

    private EntityDefinition entityDefinition;

    private List<Person> persons = new ArrayList<>();

    public StructureAndPersonHolder addPerson(Person person){
        persons.add(person);
        return this;
    }

    public Person getFirstPerson(){
        return persons.getFirst();
    }

}
