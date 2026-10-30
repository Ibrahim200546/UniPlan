package kz.uniplan.app.domain;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import kz.uniplan.app.data.local.entity.TaskEntity;
import kz.uniplan.app.domain.model.AssignmentItem;
import kz.uniplan.app.domain.model.ExamItem;
import kz.uniplan.app.domain.model.StudyItem;
import kz.uniplan.app.util.Validation;

public class ValidationTest {
    @Test public void requiredAndTitleRejectBlankAndOversize() {
        assertFalse(Validation.required(null)); assertFalse(Validation.required(" \n\t"));
        assertFalse(Validation.title("x".repeat(201))); assertTrue(Validation.title(" Қазақ тілі "));
    }
    @Test public void emailAndRegistrationValidateFieldsAndConfirmation() {
        assertFalse(Validation.email("person"));assertFalse(Validation.email("person @example.kz"));
        assertTrue(Validation.email("student+test@example.kz"));
        assertFalse(Validation.registration("Ибраһим","s@example.kz","12345","12345"));
        assertFalse(Validation.registration("Ибраһим","s@example.kz","123456","123457"));
        assertTrue(Validation.registration("Ибраһим","s@example.kz","123456","123456"));
    }
    @Test public void emailRejectsBrokenLabelsAndMultipleAddresses() {
        for(String value:Arrays.asList("a@@example.kz",".a@example.kz","a..b@example.kz","a@example..kz","a@-example.kz","a@example.kz/path","a@example.","a@localhost")) assertFalse(value,Validation.email(value));
        assertTrue(Validation.email(" student.name+tag@university.edu.kz "));
    }
    @Test public void loginDoesNotApplyNewAccountPasswordPolicy() {
        assertFalse(Validation.login("student@example.kz",""));assertFalse(Validation.login("student@example.kz",null));
        assertFalse(Validation.login("invalid","secret"));
        assertTrue(Validation.login("student@example.kz","12345"));
        // Existing accounts are checked by Firebase; only registration uses the minimum.
        assertFalse(Validation.registration("Student","student@example.kz","12345","12345"));
    }
    @Test public void registrationRequiresNameAndMatchingConfirmation() {
        assertFalse(Validation.registration(" ","student@example.kz","secret", "secret"));
        assertFalse(Validation.registration("Student","student@example.kz","secret", null));
        assertFalse(Validation.registration("Student","student@example.kz",null, null));
        assertFalse(Validation.registration("Student","student@example.kz","secret", "different"));
    }
    @Test public void taskRequiresSubjectAndValidEnums() {
        assertTrue(Validation.task("Емтихан",1,1,100,2));
        assertFalse(Validation.task("Емтихан",0,1,100,2));
        assertFalse(Validation.task("Емтихан",1,2,100,2));
        assertFalse(Validation.task("Емтихан",1,0,0,1));
        assertFalse(Validation.task("Емтихан",1,0,100,3));
    }
    @Test public void overdueIsDerivedAndCompletedNeverOverdue() {
        assertFalse(Validation.overdue(false,100,100));
        assertTrue(Validation.overdue(false,100,101));
        assertFalse(Validation.overdue(true,100,101));
        assertFalse(Validation.overdue(false,102,101));
    }
    @Test public void scheduleRejectsOvernightAndEqualIntervals() {
        assertTrue(Validation.lesson(1,0,1));assertTrue(Validation.lesson(7,1380,1439));
        assertFalse(Validation.lesson(0,600,660));assertFalse(Validation.lesson(8,600,660));
        assertFalse(Validation.lesson(1,600,600));assertFalse(Validation.lesson(1,1380,30));
        assertFalse(Validation.lesson(1,-1,30));assertFalse(Validation.lesson(1,100,1440));
    }
    @Test public void sortingHasDeterministicTies() {
        TaskEntity a=task(1,300,1), b=task(2,100,2), c=task(3,100,1), d=task(4,100,1);
        List<TaskEntity> values=new ArrayList<>(Arrays.asList(d,a,c,b));
        values.sort(StudyItem.BY_DUE);assertEquals(Arrays.asList(b,c,d,a),values);
        values.sort(StudyItem.BY_PRIORITY);assertEquals(Arrays.asList(b,c,d,a),values);
        a.priority=2;values.sort(StudyItem.BY_PRIORITY);assertEquals(Arrays.asList(b,a,c,d),values);
    }
    @Test public void polymorphicFactoryUsesConcreteKindsAndImmutableFields() {
        TaskEntity entity=task(5,100,2);entity.title="Бақылау";
        StudyItem assignment=StudyItem.from(entity);assertTrue(assignment instanceof AssignmentItem);
        entity.kind=1;StudyItem exam=StudyItem.from(entity);assertTrue(exam instanceof ExamItem);
        assertEquals("assignment",assignment.typeKey());assertEquals("exam",exam.typeKey());
        entity.title="Changed";assertEquals("Бақылау",assignment.getTitle());
        assertTrue(exam.isOverdue(101));assertEquals(1,exam.type());
    }
    @Test(expected=IllegalArgumentException.class) public void domainRejectsInvalidState() {
        new AssignmentItem(1," ",100,1,false);
    }
    private TaskEntity task(long id,long due,int priority) {
        TaskEntity value=new TaskEntity();value.id=id;value.title="Task";value.dueAt=due;value.priority=priority;return value;
    }
}
