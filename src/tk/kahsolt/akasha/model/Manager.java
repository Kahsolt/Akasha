package tk.kahsolt.akasha.model;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;

public class Manager {

    // Kernels & Caches
    private Class<? extends Model> clazz;
    private HashSet<? extends Model> collection;

    public Manager(Class<? extends Model> clazz, HashSet<? extends Model> collection) {
        this.clazz = clazz;
        this.collection = collection;
    }

    // Operations on the cached collection
    private enum CompareOperator {
        NULL, NOT_NULL,
        EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,
        BETWEEN, LIKE
    }
    public class Filter {
        private HashSet<Model> results;  // getResults()之前的查询集合缓冲区
        private Filter() { this.results = new HashSet<>(collection); }

        private boolean compare(Object lvalue, Object rvalue, CompareOperator operator) {
            Class<?> type = lvalue.getClass();
            if(TypeMap.isNumeric(type)) {
                double rval;
                if(rvalue instanceof Number || rvalue instanceof String) {
                    try {
                        rval = Double.parseDouble(rvalue.toString());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else return false;
                double lval = Double.parseDouble(rvalue.toString());
                switch (operator) {
                    case EQUAL:         return lval == rval;
                    case NOT_EQUAL:     return lval != rval;
                    case GREATER:       return lval > rval;
                    case GREATER_EQUAL: return lval >= rval;
                    case LESS:          return lval < rval;
                    case LESS_EQUAL:    return lval <= rval;
                }
            } else if(TypeMap.isTemporal(type)) {
                Date rval;
                if(rvalue instanceof Date) {
                    rval = ((Date) rvalue);
                } else if (rvalue instanceof String) {
                    try {
                        rval = DateFormat.getInstance().parse((String) rvalue);
                    } catch (ParseException e) {
                        return false;
                    }
                } else return false;
                Date lval = ((Date) lvalue);
                switch (operator) {
                    case EQUAL:         return lval.equals(rval);
                    case NOT_EQUAL:     return !lval.equals(rval);
                    case GREATER:       return lval.after(rval);
                    case GREATER_EQUAL: return lval.after(rval) || lval.equals(rval);
                    case LESS:          return lval.before(rval);
                    case LESS_EQUAL:    return lval.before(rval) || lval.equals(rval);
                }
            } else {    // Textual
                String rval = rvalue.toString();
                String lval = lvalue.toString();
                switch (operator) {
                    case EQUAL:         return lval.equals(rval);
                    case NOT_EQUAL:     return !lval.equals(rval);
                    case GREATER:       return lval.compareTo(rval) > 0;
                    case GREATER_EQUAL: return lval.compareTo(rval) >= 0;
                    case LESS:          return lval.compareTo(rval) < 0;
                    case LESS_EQUAL:    return lval.compareTo(rval) <= 0;
                }
            }
            return false;
        }
        private Filter filterByOperator(String field, CompareOperator operator) {
            HashSet<Model> pass = new HashSet<>();
            for (Model model : results) {
                try {
                    Field f = clazz.getDeclaredField(field);
                    f.setAccessible(true);
                    Object val = f.get(model);
                    switch (operator) {
                        case NULL:
                            if(val!=null) pass.add(model); break;
                        case NOT_NULL:
                            if(val==null) pass.add(model); break;
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    pass.add(model);
                }
            }
            results.removeAll(pass);
            return this;
        }
        private Filter filterByOperator(String field, CompareOperator operator, Object value) {
            HashSet<Model> pass = new HashSet<>();
            for (Model model : results) {
                try {
                    Field f = clazz.getDeclaredField(field);
                    f.setAccessible(true);
                    Object val = f.get(model);
                    switch (operator) {
                        case EQUAL:
                        case NOT_EQUAL:
                        case GREATER:
                        case GREATER_EQUAL:
                        case LESS:
                        case LESS_EQUAL:
                            if(!compare(val, value, operator))
                                pass.add(model);
                            break;
                        case LIKE:  // only valid for String/UUID
                            if(!(TypeMap.isTextual(f.getType()) && (val.toString().contains(value.toString()))))
                                pass.add(model);
                            break;
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    pass.add(model);
                }
            }
            results.removeAll(pass);
            return this;
        }
        private Filter filterByOperator(String field, CompareOperator operator, Object minValue, Object maxValue) {
            HashSet<Model> pass = new HashSet<>();
            for (Model model : results) {
                try {
                    Field f = clazz.getDeclaredField(field);
                    f.setAccessible(true);
                    Object val = f.get(model);
                    switch (operator) {
                        case BETWEEN:
                            if(compare(val, minValue, CompareOperator.LESS)) { pass.add(model); break; }
                            if(compare(val, maxValue, CompareOperator.GREATER)) { pass.add(model); break; }
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    pass.add(model);
                }
            }
            results.removeAll(pass);
            return this;
        }
        public Filter filterNull(String field) { return filterByOperator(field, CompareOperator.NULL); }
        public Filter filterNotNull(String field) { return filterByOperator(field, CompareOperator.NOT_NULL); }
        public Filter filterEqual(String field, Object value) { return filterByOperator(field, CompareOperator.EQUAL, value); }
        public Filter filterNotEqual(String field, Object value) { return filterByOperator(field, CompareOperator.NOT_EQUAL, value); }
        public Filter filterGreater(String field, Object value) { return filterByOperator(field, CompareOperator.GREATER, value); }
        public Filter filterGreaterEqual(String field, Object value) { return filterByOperator(field, CompareOperator.GREATER_EQUAL, value); }
        public Filter filterLess(String field, Object value) { return filterByOperator(field, CompareOperator.LESS, value); }
        public Filter filterLessEqual(String field, Object value) { return filterByOperator(field, CompareOperator.LESS_EQUAL, value); }
        public Filter filterLike(String field, Object value) { return filterByOperator(field, CompareOperator.LIKE, value);}
        public Filter filterBetween(String field, Object minValue, Object maxValue) { return filterByOperator(field, CompareOperator.BETWEEN, minValue, maxValue);}

        public HashSet<Model> getResults() { return results; }
    }
    public Filter filterNull(String field) { return new Filter().filterNull(field); }
    public Filter filterNotNull(String field) { return new Filter().filterNotNull(field); }
    public Filter filterEqual(String field, Object value) { return new Filter().filterEqual(field, value); }
    public Filter filterNotEqual(String field, Object value) { return new Filter().filterNotEqual(field, value); }
    public Filter filterGreater(String field, Object value) { return new Filter().filterGreater(field , value); }
    public Filter filterGreaterEqual(String field, Object value) { return new Filter().filterGreaterEqual(field, value); }
    public Filter filterLess(String field, Object value) { return new Filter().filterLess(field, value); }
    public Filter filterLessEqual(String field, Object value) { return new Filter().filterLessEqual(field, value); }
    public Filter filterBetween(String field, Object minValue, Object maxValue) { return new Filter().filterBetween(field, minValue, maxValue); }
    public Filter filterLike(String field, Object value) { return new Filter().filterLike(field, value);}
    public HashSet<Model> all() { return new HashSet<>(collection); }
    public Model get(String field, Object value) {  // shortcut
        HashSet<Model> res = filterEqual(field, value).getResults();
        return res.size()==1 ? res.iterator().next() : null;
    }

    public void saveAll() {  // shortcut
        Model.beginUpdate();
        for (Model model : collection) {
            try {
                model.save();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Model.endUpdate();
    }

}
