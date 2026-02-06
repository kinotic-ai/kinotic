import { faker } from '@faker-js/faker'
import { Patient } from './Patient'
import { Provider } from './Provider'
import { Diagnosis } from './Diagnosis'
import { Treatment } from './Treatment'
import { Appointment } from './Appointment'
import { Address } from './Address'
import { ContactInfo } from './ContactInfo'
import { InsuranceInfo } from './InsuranceInfo'
import { Qualifications } from './Qualifications'
import { Specialty } from './Specialty'
import { EducationLevel, 
         TrainingType, 
         CertificationType, 
         LicenseType, 
         Status, 
         TrialPhase, 
         Education, 
         MedicalTraining, 
         Certification, 
         License, 
         ClinicalResearch, 
         BasicScienceResearch, 
         TranslationalResearch, 
         LeadershipActivity, 
         ServiceActivity, 
         GrantFunding, 
         IndustryFunding, 
         ClinicalEvaluation, 
         AcademicEvaluation } from './Qualifications'
import { AppointmentStatus } from './AppointmentStatus'
import { AppointmentType } from './AppointmentType'
import { DiagnosisStatus } from './DiagnosisStatus'
import { TreatmentStatus } from './TreatmentStatus'
import { Prescription } from './Prescription'
import { PrescriptionStatus } from './PrescriptionStatus'
import { Gender } from './Gender'
import { BloodType } from './BloodType'

export class TestDataGenerator {
    private static readonly SPECIALTIES = [
        'Cardiology', 'Dermatology', 'Endocrinology', 'Gastroenterology',
        'Neurology', 'Oncology', 'Orthopedics', 'Pediatrics',
        'Psychiatry', 'Rheumatology', 'Urology', 'Ophthalmology'
    ]

    private static readonly CONDITIONS = [
        'Hypertension', 'Type 2 Diabetes', 'Asthma', 'Arthritis',
        'Migraine', 'Anxiety', 'Depression', 'Obesity',
        'Heart Disease', 'Chronic Pain', 'Fibromyalgia', 'Sleep Apnea'
    ]

    private static readonly TREATMENTS = [
        'Medication', 'Physical Therapy', 'Surgery', 'Counseling',
        'Lifestyle Changes', 'Dietary Changes', 'Exercise Program',
        'Alternative Therapy', 'Rehabilitation', 'Preventive Care'
    ]

    private static readonly MEDICATIONS = [
        'Lisinopril', 'Metformin', 'Atorvastatin', 'Levothyroxine',
        'Amlodipine', 'Metoprolol', 'Omeprazole', 'Albuterol',
        'Gabapentin', 'Hydrochlorothiazide', 'Sertraline', 'Simvastatin'
    ]

    private static readonly DOSAGE_FORMS = [
        'Tablet', 'Capsule', 'Liquid', 'Injection',
        'Inhaler', 'Cream', 'Ointment', 'Patch'
    ]

    private static readonly FREQUENCIES = [
        'Once daily', 'Twice daily', 'Three times daily',
        'Every 4 hours', 'Every 6 hours', 'Every 8 hours',
        'Every 12 hours', 'As needed'
    ]

    static generatePatient(): Patient {
        const patient = Patient.builder()
            .withFirstName(faker.person.firstName())
            .withLastName(faker.person.lastName())
            .withDateOfBirth(faker.date.birthdate({ min: 0, max: 100, mode: 'age' }))
            .withGender(faker.helpers.arrayElement(Object.values(Gender)))
            .withMrn(faker.string.alphanumeric(8).toUpperCase())
            .withAddresses([this.generateAddress()])
            .withContactInfo(this.generateContactInfo())
            .withInsuranceInfo(this.generateInsuranceInfo())
            .withMedicalHistory(faker.helpers.arrayElements(this.CONDITIONS, { min: 0, max: 3 }))
            .withAllergies(faker.helpers.arrayElements(['Penicillin', 'Peanuts', 'Shellfish', 'Latex', 'Pollen'], { min: 0, max: 3 }))
            .withBloodType(faker.helpers.arrayElement(Object.values(BloodType)))
            .withEmergencyContact(this.generateContactInfo())
            .withCreatedAt(faker.date.past())
            .withUpdatedAt(faker.date.recent())
            .build()

        return patient
    }

    static generateProvider(): Provider {
        const provider = Provider.builder()
            .withFirstName(faker.person.firstName())
            .withLastName(faker.person.lastName())
            .withTitle(faker.helpers.arrayElement(['Dr.', 'Prof.', 'MD', 'DO']))
            .withSpecialties([this.generateSpecialty()])
            .withQualifications(this.generateQualification())
            .withAddresses([this.generateAddress()])
            .withContactInfo(this.generateContactInfo())
            .withLicenseNumber(faker.string.alphanumeric(8).toUpperCase())
            .withLicenseExpiryDate(faker.date.future())
            .withIsActive(faker.datatype.boolean())
            .withCreatedAt(faker.date.past())
            .withUpdatedAt(faker.date.recent())
            .build()

        return provider
    }

    static generateDiagnosis(patientId: string, providerId: string): Diagnosis {
        const diagnosis = Diagnosis.builder()
            .withPatientId(patientId)
            .withProviderId(providerId)
            .withCondition(faker.helpers.arrayElement(this.CONDITIONS))
            .withDescription(faker.lorem.paragraph())
            .withStatus(faker.helpers.arrayElement(Object.values(DiagnosisStatus)))
            .withDiagnosisDate(faker.date.recent())
            .withSeverity(faker.helpers.arrayElement(['Mild', 'Moderate', 'Severe']))
            .withNotes(faker.lorem.paragraphs(2))
            .withCreatedAt(faker.date.past())
            .withUpdatedAt(faker.date.recent())
            .build()

        return diagnosis
    }

    static generateTreatment(patientId: string, providerId: string, diagnosisId: string): Treatment {
        const treatment = Treatment.builder()
            .withPatientId(patientId)
            .withProviderId(providerId)
            .withDiagnosisId(diagnosisId)
            .withName(faker.helpers.arrayElement(this.TREATMENTS))
            .withDescription(faker.lorem.paragraph())
            .withStatus(faker.helpers.arrayElement(Object.values(TreatmentStatus)))
            .withStartDate(faker.date.recent())
            .withEndDate(faker.date.future())
            .withFrequency(faker.helpers.arrayElement(['Daily', 'Weekly', 'Monthly', 'As needed']))
            .withDosage(faker.helpers.arrayElement(['10mg', '20mg', '50mg', '100mg', 'As prescribed']))
            .withNotes(faker.lorem.paragraphs(2))
            .withCreatedAt(faker.date.past())
            .withUpdatedAt(faker.date.recent())
            .build()

        return treatment
    }

    static generateAppointment(patientId: string, providerId: string): Appointment {
        const startTime = faker.date.future()
        const duration = faker.helpers.arrayElement([15, 30, 45, 60])
        const endTime = new Date(startTime.getTime() + duration * 60000)

        const appointment = Appointment.builder()
            .withPatientId(patientId)
            .withProviderId(providerId)
            .withType(faker.helpers.arrayElement(Object.values(AppointmentType)))
            .withStatus(faker.helpers.arrayElement(Object.values(AppointmentStatus)))
            .withStartTime(startTime)
            .withEndTime(endTime)
            .withDuration(duration)
            .withNotes(faker.lorem.paragraph())
            .withIsRecurring(faker.datatype.boolean())
            .withRecurrencePattern(faker.helpers.arrayElement(['Daily', 'Weekly', 'Monthly', 'None']))
            .withCreatedAt(faker.date.past())
            .withUpdatedAt(faker.date.recent())
            .build()

        return appointment
    }

    static generatePrescription(): Prescription {
        const startDate = faker.date.recent()
        const duration = faker.number.int({ min: 7, max: 90 }) // Duration in days
        const endDate = new Date(startDate.getTime() + duration * 24 * 60 * 60 * 1000)

        return Prescription.builder()
            .withMedicationName(faker.helpers.arrayElement(this.MEDICATIONS))
            .withDosageForm(faker.helpers.arrayElement(this.DOSAGE_FORMS))
            .withStrength(faker.helpers.arrayElement(['5mg', '10mg', '20mg', '50mg', '100mg', '250mg', '500mg']))
            .withFrequency(faker.helpers.arrayElement(this.FREQUENCIES))
            .withQuantity(faker.number.int({ min: 30, max: 180 }))
            .withRefillsRemaining(faker.number.int({ min: 0, max: 5 }))
            .withStartDate(startDate)
            .withEndDate(endDate)
            .withInstructions(faker.lorem.sentence())
            .withPharmacyName(faker.company.name())
            .withPharmacyPhone(faker.phone.number())
            .withStatus(faker.helpers.arrayElement(Object.values(PrescriptionStatus)))
            .withNotes(faker.lorem.paragraph())
            .withIsActive(faker.datatype.boolean())
            .build()
    }

    private static generateAddress(): Address {
        return Address.builder()
            .withStreet(faker.location.streetAddress())
            .withCity(faker.location.city())
            .withState(faker.location.state())
            .withPostalCode(faker.location.zipCode())
            .withCountry(faker.location.country())
            .build()
    }

    private static generateContactInfo(): ContactInfo {
        return ContactInfo.builder()
            .withEmail(faker.internet.email())
            .withPhoneNumber(faker.phone.number())
            .withEmergencyContactName(faker.person.fullName())
            .build()
    }

    private static generateInsuranceInfo(): InsuranceInfo {
        return InsuranceInfo.builder()
            .withProviderName(faker.helpers.arrayElement(['Blue Cross', 'Aetna', 'UnitedHealthcare', 'Cigna', 'Kaiser Permanente']))
            .withPolicyNumber(faker.string.alphanumeric(10).toUpperCase())
            .withGroupNumber(faker.string.alphanumeric(8).toUpperCase())
            .withCoverageStartDate(faker.date.past())
            .withCoverageEndDate(faker.date.future())
            .withIsPrimary(faker.datatype.boolean())
            .withCopayAmount(faker.number.float({ min: 10, max: 50, fractionDigits: 2 }))
            .withDeductibleAmount(faker.number.float({ min: 500, max: 5000, fractionDigits: 2 }))
            .withOutOfPocketMaximum(faker.number.float({ min: 1000, max: 10000, fractionDigits: 2 }))
            .withIsActive(faker.datatype.boolean())
            .build()
    }

    private static generateQualification(): Qualifications {
        const qualifications = new Qualifications()
        
        // Generate education
        const education = new Education()
        education.level = faker.helpers.arrayElement(Object.values(EducationLevel))
        education.degree = faker.helpers.arrayElement(['MD', 'DO', 'PhD', 'MPH', 'MSN'])
        education.institution = faker.company.name()
        education.year = faker.number.int({ min: 1990, max: 2024 })
        education.major = faker.helpers.arrayElement(['Medicine', 'Biology', 'Chemistry', 'Public Health'])
        education.gpa = faker.number.float({ min: 3.0, max: 4.0, fractionDigits: 2 })
        
        // Add complex research activities (nested unions)
        const clinicalResearch = new ClinicalResearch()
        clinicalResearch.trialPhase = faker.helpers.arrayElement(Object.values(TrialPhase))
        clinicalResearch.patientCount = faker.number.int({ min: 50, max: 1000 })
        clinicalResearch.outcomes = ['Improved survival', 'Reduced side effects', 'Better quality of life']
        clinicalResearch.regulatoryApproval = 'FDA Approved'
        
        // Add funding to research (nested union within union)
        const grantFunding = new GrantFunding()
        grantFunding.source = 'NIH'
        grantFunding.amount = faker.number.int({ min: 100000, max: 2000000 })
        grantFunding.duration = 3
        grantFunding.role = 'Principal Investigator'
        clinicalResearch.funding.push(grantFunding)
        
        const industryFunding = new IndustryFunding()
        industryFunding.company = faker.company.name()
        industryFunding.amount = faker.number.int({ min: 50000, max: 500000 })
        industryFunding.purpose = 'Clinical trial support'
        clinicalResearch.funding.push(industryFunding)
        
        education.research.push(clinicalResearch)
        
        // Add basic science research
        const basicResearch = new BasicScienceResearch()
        basicResearch.laboratory = faker.company.name()
        basicResearch.techniques = ['PCR', 'Western Blot', 'Cell Culture', 'Microscopy']
        basicResearch.publications = ['Nature', 'Science', 'Cell']
        basicResearch.collaborations = [faker.company.name(), faker.company.name()]
        basicResearch.equipment = ['Microscope', 'Centrifuge', 'Incubator']
        education.research.push(basicResearch)
        
        // Add complex activities (nested unions)
        const leadershipActivity = new LeadershipActivity()
        leadershipActivity.position = 'Department Chair'
        leadershipActivity.organization = faker.company.name()
        leadershipActivity.teamSize = faker.number.int({ min: 10, max: 50 })
        leadershipActivity.budget = faker.number.int({ min: 1000000, max: 10000000 })
        leadershipActivity.achievements = ['Increased efficiency', 'Improved patient outcomes', 'Reduced costs']
        education.activities.push(leadershipActivity)
        
        const serviceActivity = new ServiceActivity()
        serviceActivity.service = 'Free Clinic'
        serviceActivity.population = 'Underserved communities'
        serviceActivity.hours = faker.number.int({ min: 100, max: 500 })
        serviceActivity.impact = 'Provided care to 1000+ patients'
        serviceActivity.recognition = 'Community Service Award'
        education.activities.push(serviceActivity)
        
        qualifications.education.push(education)
        
        // Generate training
        const training = new MedicalTraining()
        training.type = faker.helpers.arrayElement(Object.values(TrainingType))
        training.specialty = faker.helpers.arrayElement(['Internal Medicine', 'Surgery', 'Pediatrics', 'Psychiatry'])
        training.institution = faker.company.name()
        training.startDate = faker.date.past()
        training.endDate = faker.date.future()
        training.supervisor = faker.person.fullName()
        
        // Add complex evaluations (nested unions)
        const clinicalEvaluation = new ClinicalEvaluation()
        clinicalEvaluation.patientCare = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        clinicalEvaluation.medicalKnowledge = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        clinicalEvaluation.practiceBasedLearning = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        clinicalEvaluation.interpersonalSkills = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        clinicalEvaluation.professionalism = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        clinicalEvaluation.systemsBasedPractice = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        training.evaluations.push(clinicalEvaluation)
        
        const academicEvaluation = new AcademicEvaluation()
        academicEvaluation.teaching = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        academicEvaluation.research = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        academicEvaluation.service = faker.number.float({ min: 3.5, max: 5.0, fractionDigits: 1 })
        academicEvaluation.publications = faker.number.int({ min: 5, max: 50 })
        academicEvaluation.grants = faker.number.int({ min: 1, max: 10 })
        training.evaluations.push(academicEvaluation)
        
        // Add research activities to training
        const translationalResearch = new TranslationalResearch()
        translationalResearch.benchToBedside = true
        translationalResearch.clinicalApplications = ['Drug development', 'Diagnostic tools', 'Treatment protocols']
        translationalResearch.industryPartnerships = [faker.company.name(), faker.company.name()]
        translationalResearch.patents = ['US Patent 123456', 'US Patent 789012']
        training.research.push(translationalResearch)
        
        qualifications.training.push(training)
        
        // Generate certification
        const certification = new Certification()
        certification.type = faker.helpers.arrayElement(Object.values(CertificationType))
        certification.name = faker.helpers.arrayElement(['Board Certified', 'Fellowship', 'Specialty Certification'])
        certification.issuingBody = faker.company.name()
        certification.yearObtained = faker.number.int({ min: 1990, max: 2024 })
        certification.status = Status.ACTIVE
        qualifications.certifications.push(certification)
        
        // Generate license
        const license = new License()
        license.type = faker.helpers.arrayElement(Object.values(LicenseType))
        license.number = faker.string.alphanumeric(8).toUpperCase()
        license.issuingState = faker.location.state()
        license.issueDate = faker.date.past()
        license.expiryDate = faker.date.future()
        license.status = Status.ACTIVE
        qualifications.licenses.push(license)
        
        return qualifications
    }

    private static generateSpecialty(): Specialty {
        return Specialty.builder()
            .withName(faker.helpers.arrayElement(this.SPECIALTIES))
            .withDescription(faker.lorem.sentence())
            .withCertificationRequired(faker.datatype.boolean())
            .withYearsOfTraining(faker.number.int({ min: 3, max: 8 }))
            .withIsActive(true)
            .build()
    }

    static generateTestData(count: number = 20): {
        patients: Patient[],
        providers: Provider[],
        diagnoses: Diagnosis[],
        treatments: Treatment[],
        appointments: Appointment[]
    } {
        const patients = Array.from({ length: count }, () => this.generatePatient())
        const providers = Array.from({ length: count }, () => this.generateProvider())
        
        // Generate diagnoses for each patient from random providers
        const diagnoses = patients.flatMap(patient => 
            Array.from({ length: faker.number.int({ min: 1, max: 3 }) }, () => 
                this.generateDiagnosis(
                    patient.id!,
                    faker.helpers.arrayElement(providers).id!
                )
            )
        )

        // Generate treatments for each diagnosis and associate prescriptions
        const treatments = diagnoses.flatMap(diagnosis => 
            Array.from({ length: faker.number.int({ min: 1, max: 2 }) }, () => {
                const treatment = this.generateTreatment(
                    diagnosis.patientId,
                    diagnosis.providerId,
                    diagnosis.id!
                )
                // Generate and associate prescriptions with the treatment
                treatment.prescriptions = Array.from(
                    { length: faker.number.int({ min: 1, max: 3 }) }, 
                    () => this.generatePrescription()
                )
                return treatment
            })
        )

        // Generate appointments for each patient with random providers
        // Some appointments can be associated with treatments
        const appointments = patients.flatMap(patient => 
            Array.from({ length: faker.number.int({ min: 1, max: 5 }) }, () => {
                const appointment = this.generateAppointment(
                    patient.id!,
                    faker.helpers.arrayElement(providers).id!
                )
                // Randomly associate some appointments with treatments
                if (faker.datatype.boolean()) {
                    const patientTreatments = treatments.filter(t => t.patientId === patient.id)
                    if (patientTreatments.length > 0) {
                        appointment.treatmentId = faker.helpers.arrayElement(patientTreatments).id!
                    }
                }
                return appointment
            })
        )

        return {
            patients,
            providers,
            diagnoses,
            treatments,
            appointments
        }
    }
} 