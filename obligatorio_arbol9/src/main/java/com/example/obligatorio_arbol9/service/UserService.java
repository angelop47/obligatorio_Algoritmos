package com.example.obligatorio_arbol9.service;

import com.example.obligatorio_arbol9.dto.*;
import com.example.obligatorio_arbol9.entity.ConfirmationStatus;
import com.example.obligatorio_arbol9.entity.User;
import com.example.obligatorio_arbol9.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // Registro de usuario
    @Transactional
    public User registerUser(UserDTO userDTO) {
        // Verificar si el email ya existe
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("El email ya está registrado.");
        }

        User user = User.builder()
                .nombre(userDTO.getNombre())
                .email(userDTO.getEmail())
                .fechaNacimiento(userDTO.getFechaNacimiento())
                .fechaFallecimiento(userDTO.getFechaFallecimiento())
                .grado(0) // Grado 0 para el usuario de referencia
                .confirmationStatus(ConfirmationStatus.PENDING)
                .build();

        // Determinar si necesita confirmación
        if (isAdult(user)) {
            // Si es mayor de edad y no está fallecido, enviar invitación al propio usuario
            if (user.getFechaFallecimiento() == null) {

            } else {
                // Si está fallecido, necesita al menos 3 confirmaciones de familiares directos
            }
        } else {
            // Si es menor de edad, necesita confirmación de un progenitor o familiar de hasta segundo grado
        }

        return userRepository.save(user);
    }

    // Método para confirmar el registro de un usuario
    @Transactional
    public void confirmUser(Long userId, ConfirmationRequest request) {
        Optional<User> optionalUser = userRepository.findById(userId);
        Optional<User> optionalConfirmer = userRepository.findById(request.getConfirmerId());

        if (optionalUser.isPresent() && optionalConfirmer.isPresent()) {
            User user = optionalUser.get();
            User confirmer = optionalConfirmer.get();

            // Verificar si el confirmador es elegible
            if (isEligibleConfirmer(user, confirmer)) {
                // Agregar el confirmador al conjunto 'confirmedBy'
                user.getConfirmedBy().add(confirmer);
                userRepository.save(user);

                // Verificar si se cumplen las condiciones de confirmación
                if (shouldConfirm(user)) {
                    user.setConfirmationStatus(ConfirmationStatus.CONFIRMED);
                    userRepository.save(user);
                }
            } else {
                throw new RuntimeException("El usuario que confirma no es elegible.");
            }
        } else {
            throw new RuntimeException("Usuario o confirmador no encontrado.");
        }
    }


    // Método para determinar si una persona es mayor de edad
    private boolean isAdult(User user) {
        if (user.getFechaNacimiento() == null) return false;
        return Period.between(user.getFechaNacimiento(), LocalDate.now()).getYears() >= 18;
    }

    // Método para verificar la elegibilidad del confirmador
    private boolean isEligibleConfirmer(User user, User confirmer) {
        if (user.getFechaFallecimiento() != null) {
            // Si está fallecido, el confirmador debe ser un familiar directo
            Set<User> directFamily = new HashSet<>();
            directFamily.addAll(user.getPadres());
            directFamily.addAll(user.getHijos());
            directFamily.addAll(user.getConyuges());
            return directFamily.contains(confirmer);
        } else if (isAdult(user)) {
            // Si es mayor de edad y no está fallecido, el propio usuario puede confirmar
            return user.equals(confirmer);
        } else {
            // Si es menor de edad, el confirmador debe ser un progenitor o familiar de hasta segundo grado
            return isWithinDegree(user, confirmer, 2);
        }
    }

    // Método para verificar si el confirmador está dentro del grado permitido
    private boolean isWithinDegree(User user, User confirmer, int maxDegree) {
        if (user.equals(confirmer)) {
            return true;
        }
        Set<User> visited = new HashSet<>();
        Queue<User> queue = new LinkedList<>();
        queue.add(user);
        visited.add(user);
        int degree = 0;

        while (!queue.isEmpty() && degree < maxDegree) {
            int size = queue.size();
            degree++;
            for (int i = 0; i < size; i++) {
                User current = queue.poll();
                Set<User> relatives = new HashSet<>();
                relatives.addAll(current.getPadres());
                relatives.addAll(current.getHijos());
                relatives.addAll(current.getConyuges());

                for (User relative : relatives) {
                    if (relative.equals(confirmer)) {
                        return true;
                    }
                    if (!visited.contains(relative)) {
                        visited.add(relative);
                        queue.add(relative);
                    }
                }
            }
        }
        return false;
    }

    private boolean shouldConfirm(User user) {
        if (user.getFechaFallecimiento() != null) {
            // Requiere al menos 3 confirmaciones de familiares directos
            return user.getConfirmedBy().size() >= 3;
        } else {
            if (isAdult(user)) {
                // Si es mayor de edad, una confirmación es suficiente
                return user.getConfirmedBy().size() >= 1;
            } else {
                // Si es menor de edad, una confirmación de un progenitor o familiar hasta segundo grado
                return user.getConfirmedBy().size() >= 1;
            }
        }
    }

    // Completar datos del usuario
    public User updateUser(Long userId, UserDTO userDTO) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setNombre(userDTO.getNombre());
            user.setFechaNacimiento(userDTO.getFechaNacimiento());
            user.setFechaFallecimiento(userDTO.getFechaFallecimiento());
            return userRepository.save(user);
        } else {
            throw new RuntimeException("Usuario no encontrado");
        }
    }

    // Añadir familiar
    @Transactional
    public void addFamilyMember(Long userId, UserDTO familyMemberDTO, String relationship) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            Integer newGrado;

            if (relationship.equalsIgnoreCase("antecesor")) {
                newGrado = user.getGrado() + 1;
            } else if (relationship.equalsIgnoreCase("sucesor")) {
                newGrado = user.getGrado() - 1;
            } else {
                throw new RuntimeException("Tipo de relación inválida");
            }

            // Verificar si el email del familiar ya existe
            if (userRepository.existsByEmail(familyMemberDTO.getEmail())) {
                throw new RuntimeException("El email del familiar ya está registrado.");
            }

            User familyMember = User.builder()
                    .nombre(familyMemberDTO.getNombre())
                    .email(familyMemberDTO.getEmail())
                    .fechaNacimiento(familyMemberDTO.getFechaNacimiento())
                    .fechaFallecimiento(familyMemberDTO.getFechaFallecimiento())
                    .grado(newGrado)
                    .confirmationStatus(ConfirmationStatus.PENDING)
                    .build();

            // Establecer relaciones bidireccionales
            if (relationship.equalsIgnoreCase("antecesor")) {
                familyMember.getHijos().add(user);
                user.getPadres().add(familyMember);
            } else if (relationship.equalsIgnoreCase("sucesor")) {
                familyMember.getPadres().add(user);
                user.getHijos().add(familyMember);
            }

            // Guardar los cambios
            userRepository.save(familyMember);
            userRepository.save(user);
        } else {
            throw new RuntimeException("Usuario no encontrado");
        }
    }


    // Añadir cónyuge
    @Transactional
    public void addSpouse(Long userId, UserDTO spouseDTO) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            // Verificar si el email del cónyuge ya existe
            if (userRepository.existsByEmail(spouseDTO.getEmail())) {
                throw new RuntimeException("El email del cónyuge ya está registrado.");
            }

            User spouse = User.builder()
                    .nombre(spouseDTO.getNombre())
                    .email(spouseDTO.getEmail())
                    .fechaNacimiento(spouseDTO.getFechaNacimiento())
                    .fechaFallecimiento(spouseDTO.getFechaFallecimiento())
                    .grado(user.getGrado()) // Mismo grado que el usuario
                    .confirmationStatus(ConfirmationStatus.PENDING)
                    .build();

            user.getConyuges().add(spouse);
            spouse.getConyuges().add(user);

            userRepository.save(spouse);
            userRepository.save(user);
        } else {
            throw new RuntimeException("Usuario no encontrado");
        }
    }


    // Método para borrar usuario
    @Transactional
    public void deleteUser(Long userId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            // Eliminar relaciones con padres
            Set<User> padres = new HashSet<>(user.getPadres());
            for (User parent : padres) {
                parent.getHijos().remove(user);
                user.getPadres().remove(parent);
            }

            // Eliminar relaciones con hijos
            Set<User> hijos = new HashSet<>(user.getHijos());
            for (User child : hijos) {
                child.getPadres().remove(user);
                user.getHijos().remove(child);
            }

            // Eliminar relaciones con cónyuges
            Set<User> conyuges = new HashSet<>(user.getConyuges());
            for (User spouse : conyuges) {
                spouse.getConyuges().remove(user);
                user.getConyuges().remove(spouse);
            }

            // Finalmente, borrar el usuario
            userRepository.delete(user);
        } else {
            throw new RuntimeException("Usuario no encontrado");
        }
    }

    // Obtener árbol genealógico
    public User getGenealogyTree(Long userId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            return optionalUser.get();
        } else {
            throw new RuntimeException("Usuario no encontrado");
        }
    }

    // Obtener tdoos los Usuarios
    public List<UserSummaryDTO> getAllUsersSummary() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(user -> UserSummaryDTO.builder()
                        .id(user.getId())
                        .nombre(user.getNombre())
                        .fechaNacimiento(user.getFechaNacimiento())
                        .fechaFallecimiento(user.getFechaFallecimiento())
                        .email(user.getEmail())
                        .confirmationStatus(user.getConfirmationStatus())
                        .build())
                .collect(Collectors.toList());
    }

    //Obtener Usuario por nombre
    public User getGenealogyTreeByName(String nombre) {
        List<User> users = userRepository.findAllByNombre(nombre);
        if (users.isEmpty()) {
            throw new RuntimeException("Usuario no encontrado");
        } else if (users.size() > 1) {
            throw new RuntimeException("Se encontraron múltiples usuarios con ese nombre");
        } else {
            return users.get(0);
        }
    }

    public List<UserSummaryDTO> getSameGenerationFamily(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Integer grado = user.getGrado();
        List<User> sameGenerationUsers = userRepository.findByGrado(grado)
                .stream()
                .sorted((u1, u2) -> u1.getFechaNacimiento().compareTo(u2.getFechaNacimiento())) // Ordena por fecha de nacimiento (mayores primero)
                .collect(Collectors.toList());

        return sameGenerationUsers.stream()
                .map(u -> UserSummaryDTO.builder()
                        .id(u.getId())
                        .nombre(u.getNombre())
                        .fechaNacimiento(u.getFechaNacimiento())
                        .fechaFallecimiento(u.getFechaFallecimiento())
                        .email(u.getEmail())
                        .confirmationStatus(u.getConfirmationStatus())
                        .build())
                .collect(Collectors.toList());
    }


    //Construye el árbol genealógico del usuario hasta un grado de profundidad específico.
    public UserTreeDTO getGenealogyTree(Long userId, int depth) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return buildUserTree(user, depth, new HashSet<>());
    }


    //Método recursivo para construir el árbol genealógico.
    private UserTreeDTO buildUserTree(User user, int depth, Set<Long> visited) {
        if (user == null || depth < 0 || visited.contains(user.getId())) {
            return null;
        }

        visited.add(user.getId());

        return UserTreeDTO.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .fechaNacimiento(user.getFechaNacimiento())
                .fechaFallecimiento(user.getFechaFallecimiento())
                .email(user.getEmail())
                .confirmationStatus(user.getConfirmationStatus())
                .padres(user.getPadres().stream()
                        .map(parent -> buildUserTree(parent, depth - 1, visited))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .hijos(user.getHijos().stream()
                        .map(child -> buildUserTree(child, depth - 1, visited))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .conyuges(user.getConyuges().stream()
                        .map(spouse -> buildUserTree(spouse, depth - 1, visited))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .build();
    }


    //obtener lista de confirmaciones pendientes
    public List<PendingConfirmationDTO> getPendingConfirmations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Obtener todos los familiares en el árbol genealógico
        Set<User> familyTree = new HashSet<>();
        traverseFamilyTree(user, 10, familyTree); // Asumiendo un límite de 10 grados para evitar ciclos

        // Filtrar aquellos que están pendientes de confirmación
        List<PendingConfirmationDTO> pendingConfirmations = familyTree.stream()
                .filter(u -> u.getConfirmationStatus() == ConfirmationStatus.PENDING)
                .map(u -> PendingConfirmationDTO.builder()
                        .id(u.getId())
                        .nombre(u.getNombre())
                        .fechaNacimiento(u.getFechaNacimiento())
                        .fechaFallecimiento(u.getFechaFallecimiento())
                        .email(u.getEmail())
                        .confirmationStatus(u.getConfirmationStatus())
                        .accionParaConfirmar(determineAction(u))
                        .build())
                .collect(Collectors.toList());

        return pendingConfirmations;
    }


    //Método recursivo para recorrer el árbol genealógico.
    private void traverseFamilyTree(User user, int depth, Set<User> familyTree) {
        if (user == null || depth < 0 || familyTree.contains(user)) {
            return;
        }

        familyTree.add(user);

        for (User parent : user.getPadres()) {
            traverseFamilyTree(parent, depth - 1, familyTree);
        }

        for (User child : user.getHijos()) {
            traverseFamilyTree(child, depth - 1, familyTree);
        }

        for (User spouse : user.getConyuges()) {
            traverseFamilyTree(spouse, depth - 1, familyTree);
        }
    }


    //Determina la acción sugerida para confirmar un usuario pendiente.
    private String determineAction(User user) {
        if (user.getFechaFallecimiento() != null) {
            return "Solicitar confirmación a al menos 3 familiares directos.";
        } else if (isAdult(user)) {
            return "Confirmar tu propio registro.";
        } else {
            return "Solicitar confirmación a un progenitor o familiar de hasta segundo grado.";
        }
    }


    public String findKinship(Long userId, String targetName) {
        User startUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario actual no encontrado"));

        User targetUser = userRepository.findByNombre(targetName)
                .orElseThrow(() -> new RuntimeException("Usuario objetivo no encontrado"));

        // Realizar BFS para encontrar el camino
        Queue<User> queue = new LinkedList<>();
        Map<Long, String> relationshipMap = new HashMap<>();
        Map<Long, String> kinshipMap = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        queue.add(startUser);
        visited.add(startUser.getId());
        relationshipMap.put(startUser.getId(), "Tú");
        kinshipMap.put(startUser.getId(), "Self");

        while (!queue.isEmpty()) {
            User currentUser = queue.poll();

            if (currentUser.equals(targetUser)) {
                return interpretKinship(kinshipMap.get(currentUser.getId()));
            }

            // Explorar familiares
            exploreRelative(currentUser.getPadres(), currentUser, "Padre/Madre", "Parent", queue, visited, relationshipMap, kinshipMap);
            exploreRelative(currentUser.getHijos(), currentUser, "Hijo/Hija", "Child", queue, visited, relationshipMap, kinshipMap);
            exploreRelative(currentUser.getConyuges(), currentUser, "Cónyuge", "Spouse", queue, visited, relationshipMap, kinshipMap);
        }

        return "No se encontró parentesco con el usuario especificado.";
    }

    private void exploreRelative(Set<User> relatives, User currentUser, String relation, String kinshipCode,
                                 Queue<User> queue, Set<Long> visited, Map<Long, String> relationshipMap, Map<Long, String> kinshipMap) {
        for (User relative : relatives) {
            if (!visited.contains(relative.getId())) {
                visited.add(relative.getId());
                queue.add(relative);
                relationshipMap.put(relative.getId(), relationshipMap.get(currentUser.getId()) + " -> " + relation);
                kinshipMap.put(relative.getId(), kinshipMap.get(currentUser.getId()) + "-" + kinshipCode);
            }
        }
    }

    private String interpretKinship(String kinshipCode) {
        // Mapear códigos de parentesco a términos familiares
        Map<String, String> kinshipDictionary = new HashMap<>();
        kinshipDictionary.put("Self-Parent", "Padre/Madre");
        kinshipDictionary.put("Self-Child", "Hijo/Hija");
        kinshipDictionary.put("Self-Spouse", "Cónyuge");
        kinshipDictionary.put("Self-Parent-Parent", "Abuelo/Abuela");
        kinshipDictionary.put("Self-Child-Child", "Nieto/Nieta");
        kinshipDictionary.put("Self-Parent-Child", "Hermano/Hermana");
        // Añade más relaciones según sea necesario

        return kinshipDictionary.getOrDefault(kinshipCode, "Parentesco distante");
    }

}